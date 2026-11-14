package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * move_type with package: a file's only top-level type moves to another package of the same source
 * root. Its file moves; every import and qualified name of it follows; files of its old package
 * import it; and what the move puts across a package boundary while package-private becomes public,
 * named in the result.
 */
final class MoveToPackage {

  private MoveToPackage() {}

  static Outcome run(SourceProject project, String rel, String name, String pkg)
      throws IOException {
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isName(pkg), () -> pkg + " is not a package name, such as a.b."));
    Opened opened = Opened.of(project, rel);
    CompilationUnit unit = opened.unit();
    AbstractTypeDeclaration decl = null;
    for (Object o : unit.types()) {
      if (o instanceof AbstractTypeDeclaration t && t.getName().getIdentifier().equals(name)) {
        decl = t;
      }
    }
    AbstractTypeDeclaration type = decl;
    String old =
        unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
    Precondition.require(
        Precondition.that(
            () -> type != null && type.resolveBinding() != null,
            () -> name + " is not a top-level type of " + rel + " that resolves."),
        Precondition.that(
            () -> unit.types().size() == 1,
            () ->
                rel
                    + " declares "
                    + unit.types().size()
                    + " top-level types. move with package moves a file's only top-level"
                    + " type: move the others to files of their own first."),
        Precondition.that(
            () -> !old.equals(pkg), () -> name + " is in package " + pkg + " already."));
    Path root = rootOf(project, opened.file());
    Path target = root.resolve(pkg.replace('.', '/')).resolve(name + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(target),
            () ->
                project.relative(target)
                    + " already exists. Rename one of the two types first, then move it."));

    ITypeBinding moved = type.resolveBinding();
    String key = moved.getTypeDeclaration().getKey();
    String oldName = old.isEmpty() ? name : old + "." + name;
    String newName = pkg + "." + name;

    Holding holding = Holding.of(project, name, opened.file());
    Map<Path, String> texts = new LinkedHashMap<>(holding.texts());
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Map<String, List<Integer>> lines = new TreeMap<>();
    Set<String> widened = new TreeSet<>();
    Map<Path, CompilationUnit> units = new LinkedHashMap<>(holding.units());
    int references = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      Path f = e.getKey();
      CompilationUnit u = e.getValue();
      String text = texts.get(f);
      String filePkg =
          u.getPackage() == null ? "" : u.getPackage().getName().getFullyQualifiedName();
      boolean self = f.equals(opened.file());
      List<TextEdit> list = edits.computeIfAbsent(f, k -> new ArrayList<>());
      List<Integer> hits = new ArrayList<>();
      List<IBinding> crossing = new ArrayList<>();
      u.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(ImportDeclaration i) {
              String imported = i.getName().getFullyQualifiedName();
              if (imported.equals(oldName) || imported.startsWith(oldName + ".")) {
                ASTNode n = i.getName();
                list.add(
                    new TextEdit(
                        n.getStartPosition(),
                        n.getStartPosition() + n.getLength(),
                        newName + imported.substring(oldName.length())));
                hits.add(u.getLineNumber(i.getStartPosition()));
              }
              return false;
            }

            @Override
            public boolean visit(QualifiedName q) {
              if (q.getQualifier().resolveBinding() instanceof IPackageBinding
                  && q.resolveBinding() instanceof ITypeBinding t
                  && key.equals(t.getTypeDeclaration().getKey())) {
                list.add(
                    new TextEdit(
                        q.getQualifier().getStartPosition(),
                        q.getQualifier().getStartPosition() + q.getQualifier().getLength(),
                        pkg));
                hits.add(u.getLineNumber(q.getStartPosition()));
                return false;
              }
              return true;
            }

            @Override
            public boolean visit(SimpleName n) {
              IBinding b = n.resolveBinding();
              if (b instanceof ITypeBinding t && key.equals(t.getTypeDeclaration().getKey())) {
                // a.Shop is handled above; Shop.ONE names it bare, as a qualifier.
                if (!self) {
                  hits.add(u.getLineNumber(n.getStartPosition()));
                }
              }
              if (b != null && !n.isDeclaration()) {
                ITypeBinding top = topLevel(declaringType(b));
                boolean ofMoved = top != null && key.equals(top.getTypeDeclaration().getKey());
                // Used from outside the new package: the moved type's own declarations.
                // Used from inside the moved type: the old package's declarations.
                if ((!self && ofMoved && !filePkg.equals(pkg))
                    || (self
                        && !ofMoved
                        && top != null
                        && !old.isEmpty()
                        && packageOf(top).equals(old))) {
                  crossing.add(b);
                }
              }
              return true;
            }
          });
      for (IBinding b : crossing) {
        widen(project, root, old, b, texts, units, edits, widened);
      }
      if (!self && !hits.isEmpty()) {
        lines.put(project.relative(f).toString(), new ArrayList<>(new TreeSet<>(hits)));
        references += hits.size();
      }
    }

    Map<Path, String> changed = new LinkedHashMap<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      Path f = e.getKey();
      String text = texts.get(f);
      CompilationUnit u = units.get(f);
      List<TextEdit> list = new ArrayList<>(e.getValue());
      if (f.equals(opened.file())) {
        if (u.getPackage() != null) {
          ASTNode p = u.getPackage().getName();
          list.add(new TextEdit(p.getStartPosition(), p.getStartPosition() + p.getLength(), pkg));
        } else {
          // The default package: the package line goes before the imports and the type.
          int at =
              !u.imports().isEmpty()
                  ? ((ASTNode) u.imports().get(0)).getStartPosition()
                  : type.getStartPosition();
          list.add(new TextEdit(at, at, "package " + pkg + ";\n\n"));
        }
        String after = TextEdit.apply(text, list, 0);
        changed.put(f, null);
        changed.put(
            target,
            withoutOwnPackage(Imports.fix(project, target, text, after, List.of()).text(), pkg));
        continue;
      }
      String filePkg =
          u.getPackage() == null ? "" : u.getPackage().getName().getFullyQualifiedName();
      String after = TextEdit.apply(text, list, 0);
      if (filePkg.equals(old) && lines.containsKey(project.relative(f).toString())) {
        after = Imports.adding(after, Jdt.parseSyntax(after), newName);
      }
      if (!after.equals(text)) {
        changed.put(f, Imports.fix(project, f, text, after, List.of(newName)).text());
      }
    }

    Stubs.Declared declared =
        Gate.applyOrRefuse(
            project,
            changed,
            "moving " + name + " to package " + pkg,
            old.isEmpty()
                ? "A package cannot import a type of the default package: move the default-package"
                    + " types that this one uses or that use it in the same batch, check \"end\"."
                : "Widen or move what the errors name first, then move " + name + " again.");
    String result = "moved " + name + " to package " + pkg + " (" + project.relative(target) + ")";
    if (references > 0) {
      List<String> each = new ArrayList<>();
      lines.forEach((r, ls) -> each.add(r + " " + Lines.list(ls)));
      result +=
          ", with "
              + references
              + (references == 1 ? " reference" : " references")
              + " in "
              + lines.size()
              + (lines.size() == 1 ? " file: " : " files: ")
              + String.join("; ", each);
    }
    if (!widened.isEmpty()) {
      List<String> all = new ArrayList<>(widened);
      String joined =
          all.size() == 1
              ? all.get(0)
              : String.join(", ", all.subList(0, all.size() - 1)) + " and " + all.getLast();
      result += "; widened " + joined + " to public";
    }
    return new Outcome(result, declared);
  }

  /**
   * Makes the declaration of {@code b} public when it is package-private, and the top-level type
   * that declares it too: a use of it crosses the new package boundary. {@code widened} gets its
   * label. A declaration whose file is not where its package says is left to the gate.
   */
  private static void widen(
      SourceProject project,
      Path root,
      String old,
      IBinding b,
      Map<Path, String> texts,
      Map<Path, CompilationUnit> units,
      Map<Path, List<TextEdit>> edits,
      Set<String> widened)
      throws IOException {
    for (IBinding each = b; each != null; each = declaringType(each)) {
      if (!(each instanceof ITypeBinding)
          && !(each instanceof IMethodBinding)
          && !(each instanceof IVariableBinding v && v.isField())) {
        return;
      }
      int mods = each.getModifiers();
      if (Modifier.isPublic(mods) || Modifier.isPrivate(mods) || Modifier.isProtected(mods)) {
        continue;
      }
      ITypeBinding top = topLevel(each instanceof ITypeBinding t ? t : declaringType(each));
      if (top == null) {
        return;
      }
      Path file =
          root.resolve(packageOf(top).replace('.', '/'))
              .resolve(top.getName() + ".java")
              .toAbsolutePath()
              .normalize();
      if (!Files.isRegularFile(file)) {
        return;
      }
      if (!texts.containsKey(file)) {
        String text = Files.readString(file);
        texts.put(file, text);
        units.put(file, Jdt.parse(project, file, text));
      }
      String label = label(each);
      if (!widened.add(label)) {
        continue;
      }
      ASTNode node = units.get(file).findDeclaringNode(declarationKey(each));
      BodyDeclaration d =
          node instanceof BodyDeclaration bd
              ? bd
              : node != null && node.getParent() instanceof BodyDeclaration bd ? bd : null;
      if (d == null) {
        widened.remove(label);
        return;
      }
      int at = publicAt(texts.get(file), d);
      edits.computeIfAbsent(file, k -> new ArrayList<>()).add(new TextEdit(at, at, "public "));
    }
  }

  /** Where {@code public } goes in {@code d}: after its Javadoc and annotations. */
  private static int publicAt(String text, BodyDeclaration d) {
    int at = d.getJavadoc() != null ? Nodes.end(d.getJavadoc()) : d.getStartPosition();
    for (Object o : d.modifiers()) {
      if (o instanceof Annotation a) {
        at = Math.max(at, Nodes.end(a));
      }
    }
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at;
  }

  private static String declarationKey(IBinding b) {
    return switch (b) {
      case ITypeBinding t -> t.getTypeDeclaration().getKey();
      case IMethodBinding m -> m.getMethodDeclaration().getKey();
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      default -> b.getKey();
    };
  }

  /** "Helper", "Helper.TAX", "Shop.price()": a widened declaration, as the result names it. */
  private static String label(IBinding b) {
    return switch (b) {
      case ITypeBinding t -> Names.typeLabel(t);
      case IMethodBinding m -> Names.typeLabel(m.getDeclaringClass()) + "." + m.getName() + "()";
      default -> Names.typeLabel(declaringType(b)) + "." + b.getName();
    };
  }

  /** The type that declares {@code b}, or null for a top-level type or a local. */
  private static ITypeBinding declaringType(IBinding b) {
    return switch (b) {
      case ITypeBinding t -> t.getDeclaringClass();
      case IMethodBinding m -> m.getDeclaringClass();
      case IVariableBinding v -> v.isField() ? v.getDeclaringClass() : null;
      default -> null;
    };
  }

  private static ITypeBinding topLevel(ITypeBinding t) {
    return t == null ? null : Names.outermost(t);
  }

  private static String packageOf(ITypeBinding t) {
    return t.getPackage() == null ? "" : t.getPackage().getName();
  }

  /** The source root that holds {@code file}. */
  private static Path rootOf(SourceProject project, Path file) {
    for (Path root : project.sourceRoots()) {
      if (file.startsWith(root)) {
        return root;
      }
    }
    throw new Refusal(project.relative(file) + " is not under a source root.");
  }

  /**
   * {@code text} without its imports of single types of package {@code pkg}, its own, and of a type
   * named by a simple name (a type of the default package, which cannot be imported); a blank line
   * left doubled where one stood is dropped.
   */
  private static String withoutOwnPackage(String text, String pkg) {
    CompilationUnit unit = Jdt.parseSyntax(text);
    List<TextEdit> drops = new ArrayList<>();
    for (Object o : unit.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      if (i.isStatic() || i.isOnDemand()) {
        continue;
      }
      boolean own =
          i.getName() instanceof QualifiedName q
              ? q.getQualifier().getFullyQualifiedName().equals(pkg)
              : true;
      if (own) {
        int start = Lines.start(text, i.getStartPosition());
        int end = Lines.end(text, Nodes.end(i));
        if (start > 1
            && text.charAt(start - 1) == '\n'
            && text.charAt(start - 2) == '\n'
            && end < text.length()
            && text.charAt(end) == '\n') {
          end++;
        }
        drops.add(new TextEdit(start, end, ""));
      }
    }
    return drops.isEmpty() ? text : TextEdit.apply(text, drops, 0);
  }
}
