package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Moves a static nested type to its own file, as a top-level type of the same package: every
 * reference in the project follows it, its uses of the outer type's static members are qualified,
 * and private members used across the new boundary become package-private.
 */
final class MoveType {

  private MoveType() {}

  /** The text from {@code start} to {@code end} replaced by {@code text}. */
  private record Edit(int start, int end, String text) {}

  /** Moves the nested type {@code name} ("Outer.Inner") declared in {@code rel}. */
  static Tools.Outcome run(SourceProject project, String rel, String name) throws IOException {
    Path file = project.javaFile(rel);
    String simple = name.substring(name.lastIndexOf('.') + 1);
    Map<Path, String> held = new LinkedHashMap<>();
    for (Path f : project.javaFiles()) {
      String text = Files.readString(f);
      if (f.equals(file) || text.contains(simple)) {
        held.put(f, text);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(held.keySet()));
    String source = held.get(file);
    CompilationUnit unit = parsed.get(file);
    AbstractTypeDeclaration decl = find(unit, name, rel);
    ITypeBinding type = decl.resolveBinding();
    if (type == null) {
      throw new Refusal(name + " does not resolve. Fix the compile errors in " + rel + " first.");
    }
    String outerLabel = name.substring(0, name.lastIndexOf('.'));
    if (decl instanceof TypeDeclaration t
        && !t.isInterface()
        && !Modifier.isStatic(t.getModifiers())) {
      throw new Refusal(
          name
              + " is an inner class (not static), so it may use an instance of "
              + outerLabel
              + ". Make it static first, passing what it uses from "
              + outerLabel
              + ", then move it.");
    }
    Path target = file.resolveSibling(simple + ".java");
    if (Files.exists(target)) {
      throw new Refusal(
          project.relative(target) + " already exists. Rename " + name + " first, then move it.");
    }
    String pkg = packageOf(unit);
    String key = type.getTypeDeclaration().getKey();
    String outerKey = type.getDeclaringClass().getTypeDeclaration().getKey();
    int start = decl.getStartPosition();
    int end = start + decl.getLength();

    Map<Path, String> changed = new LinkedHashMap<>();
    Map<String, List<Integer>> lines = new TreeMap<>();
    int references = 0;
    TreeMap<Integer, String> widened = new TreeMap<>();
    for (Map.Entry<Path, String> e : held.entrySet()) {
      Path f = e.getKey();
      String text = e.getValue();
      CompilationUnit u = parsed.get(f);
      boolean samePackage = packageOf(u).equals(pkg);
      List<Edit> edits = new ArrayList<>();
      List<ASTNode> refs = new ArrayList<>();
      boolean needsImport = references(u, text, key, pkg, simple, samePackage, edits, refs);
      if (needsImport && !imports(u, qualified(pkg, simple))) {
        edits.add(importEdit(text, u, qualified(pkg, simple)));
      }
      if (!refs.isEmpty()) {
        Set<Integer> at = new TreeSet<>();
        for (ASTNode r : refs) {
          at.add(u.getLineNumber(r.getStartPosition()));
        }
        lines.put(project.relative(f).toString(), new ArrayList<>(at));
        references += refs.size();
      }
      if (!f.equals(file)) {
        if (!edits.isEmpty()) {
          changed.put(f, apply(text, 0, edits));
        }
        continue;
      }
      qualifyAndWiden(u, text, decl, type, key, outerKey, outerLabel, simple, edits, widened);
      for (Object o : decl.modifiers()) {
        if (o instanceof Modifier m && (m.isStatic() || m.isPrivate() || m.isProtected())) {
          edits.add(dropModifier(text, m));
        }
      }
      List<Edit> inside = new ArrayList<>();
      List<Edit> outside = new ArrayList<>();
      for (Edit edit : edits) {
        (edit.start() >= start && edit.start() < end ? inside : outside).add(edit);
      }
      int lineStart = Lines.start(text, start);
      String moved = dedent(apply(text.substring(lineStart, end), lineStart, inside), text, start);
      Set<String> usedInside = new HashSet<>();
      Set<String> usedOutside = new HashSet<>();
      identifiers(u, start, end, usedInside, usedOutside);
      StringBuilder kept = new StringBuilder();
      Boolean lastStatic = null;
      for (Object o : u.imports()) {
        ImportDeclaration i = (ImportDeclaration) o;
        String last = simpleName(i.getName().getFullyQualifiedName());
        boolean used = i.isOnDemand() || usedInside.contains(last);
        if (!used) {
          continue;
        }
        if (lastStatic != null && lastStatic != i.isStatic()) {
          kept.append('\n');
        }
        lastStatic = i.isStatic();
        kept.append(
            text, Lines.start(text, i.getStartPosition()), Lines.end(text, i.getStartPosition()));
        if (!i.isOnDemand() && !usedOutside.contains(last)) {
          outside.add(
              new Edit(
                  Lines.start(text, i.getStartPosition()),
                  Lines.end(text, i.getStartPosition()),
                  ""));
        }
      }
      int cutStart = lineStart;
      int before = Lines.start(text, Math.max(0, cutStart - 1));
      if (cutStart > 0 && text.substring(before, cutStart).isBlank()) {
        cutStart = before;
      }
      outside.add(new Edit(cutStart, Lines.end(text, end), ""));
      changed.put(f, apply(text, 0, outside));
      String created =
          (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n")
              + (kept.isEmpty() ? "" : kept + "\n")
              + moved
              + "\n";
      changed.put(target, created);
    }

    Stubs.Check check = Gate.apply(project, changed);
    if (!check.introduced().isEmpty()) {
      throw new Refusal(
          "moving "
              + name
              + " to "
              + project.relative(target)
              + " would introduce compile errors, so nothing was changed:\n  "
              + String.join("\n  ", check.introduced()));
    }
    String result =
        "moved "
            + name
            + " to "
            + project.relative(target)
            + " ("
            + changed.get(target).lines().count()
            + " lines)";
    if (references > 0) {
      List<String> each = new ArrayList<>();
      lines.forEach((r, ls) -> each.add(r + " " + Lines.list(ls)));
      result +=
          ", with "
              + plural(references, "reference")
              + " in "
              + plural(lines.size(), "file")
              + ": "
              + String.join("; ", each);
    }
    if (!widened.isEmpty()) {
      result += "; widened " + joinAnd(new ArrayList<>(widened.values())) + " to package-private";
    }
    return new Tools.Outcome(result, check.declared());
  }

  /**
   * The nested type {@code name} ("Outer.Inner") in {@code unit}, or a refusal: a top-level type,
   * or no such type.
   */
  private static AbstractTypeDeclaration find(CompilationUnit unit, String name, String rel) {
    String[] parts = name.split("\\.");
    AbstractTypeDeclaration at = null;
    for (Object o : unit.types()) {
      if (o instanceof AbstractTypeDeclaration t && t.getName().getIdentifier().equals(parts[0])) {
        at = t;
      }
    }
    if (at != null && parts.length == 1) {
      String example = name + ".Inner";
      for (Object o : at.bodyDeclarations()) {
        if (o instanceof AbstractTypeDeclaration member) {
          example = name + "." + member.getName().getIdentifier();
          break;
        }
      }
      throw new Refusal(
          name
              + " is a top-level type already. move_type moves a nested type to its own file:"
              + " name it as "
              + example
              + ".");
    }
    for (int i = 1; at != null && i < parts.length; i++) {
      AbstractTypeDeclaration next = null;
      for (Object o : at.bodyDeclarations()) {
        if (o instanceof AbstractTypeDeclaration member
            && member.getName().getIdentifier().equals(parts[i])) {
          next = member;
        }
      }
      at = next;
    }
    if (at == null) {
      throw new Refusal("No nested type " + name + " in " + rel + ". Name it as Outer.Inner.");
    }
    return at;
  }

  /**
   * Adds to {@code edits} the changes that make {@code u}'s references to the type with key {@code
   * key} name it as a top-level type, and to {@code refs} those references; true when a short
   * reference ({@code Outer.Inner}) in another package now needs an import.
   */
  private static boolean references(
      CompilationUnit u,
      String text,
      String key,
      String pkg,
      String simple,
      boolean samePackage,
      List<Edit> edits,
      List<ASTNode> refs) {
    boolean[] needsImport = {false};
    u.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(PackageDeclaration p) {
            return false;
          }

          @Override
          public boolean visit(ImportDeclaration i) {
            if (!i.isStatic()
                && !i.isOnDemand()
                && i.resolveBinding() instanceof ITypeBinding t
                && t.getTypeDeclaration().getKey().equals(key)) {
              refs.add(i);
              if (samePackage) {
                edits.add(
                    new Edit(
                        Lines.start(text, i.getStartPosition()),
                        Lines.end(text, i.getStartPosition()),
                        ""));
              } else {
                edits.add(
                    new Edit(
                        i.getName().getStartPosition(),
                        i.getName().getStartPosition() + i.getName().getLength(),
                        qualified(pkg, simple)));
              }
            }
            return false;
          }

          @Override
          public boolean visit(QualifiedName q) {
            if (q.resolveBinding() instanceof ITypeBinding t
                && t.getTypeDeclaration().getKey().equals(key)) {
              refs.add(q);
              boolean short_ = q.getQualifier() instanceof SimpleName;
              edits.add(
                  new Edit(
                      q.getStartPosition(),
                      q.getStartPosition() + q.getLength(),
                      short_ ? simple : qualified(pkg, simple)));
              if (short_ && !samePackage) {
                needsImport[0] = true;
              }
              return false;
            }
            return true;
          }
        });
    return needsImport[0];
  }

  /**
   * Adds to {@code edits}, in the outer file {@code u}: the outer type's name before each
   * unqualified use, inside the moved type, of the outer type's static members and member types;
   * and the removal of {@code private} from each member used across the moved type's boundary,
   * which {@code widened} names by position.
   */
  private static void qualifyAndWiden(
      CompilationUnit u,
      String text,
      AbstractTypeDeclaration decl,
      ITypeBinding type,
      String key,
      String outerKey,
      String outerLabel,
      String simple,
      List<Edit> edits,
      Map<Integer, String> widened) {
    int start = decl.getStartPosition();
    int end = start + decl.getLength();
    u.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            IBinding b = n.resolveBinding();
            if (b == null || n.isDeclaration()) {
              return true;
            }
            boolean inside = n.getStartPosition() >= start && n.getStartPosition() < end;
            if (inside && unqualified(n) && memberOf(b, outerKey, key)) {
              edits.add(new Edit(n.getStartPosition(), n.getStartPosition(), outerLabel + "."));
            }
            widen(b, inside);
            return true;
          }

          @Override
          public boolean visit(ClassInstanceCreation c) {
            IMethodBinding b = c.resolveConstructorBinding();
            if (b != null) {
              widen(b, c.getStartPosition() >= start && c.getStartPosition() < end);
            }
            return true;
          }

          private void widen(IBinding b, boolean inside) {
            IBinding d =
                b instanceof IMethodBinding m
                    ? m.getMethodDeclaration()
                    : b instanceof IVariableBinding v
                        ? v.getVariableDeclaration()
                        : b instanceof ITypeBinding t ? t.getTypeDeclaration() : b;
            if (!Modifier.isPrivate(d.getModifiers())) {
              return;
            }
            ASTNode at = u.findDeclaringNode(d.getKey());
            BodyDeclaration body =
                at instanceof VariableDeclarationFragment f
                        && f.getParent() instanceof FieldDeclaration fd
                    ? fd
                    : at instanceof BodyDeclaration bd ? bd : null;
            if (body == null || body == decl || widened.containsKey(body.getStartPosition())) {
              return;
            }
            boolean declaredInside =
                body.getStartPosition() >= start && body.getStartPosition() < end;
            if (declaredInside == inside) {
              return;
            }
            for (Object o : body.modifiers()) {
              if (o instanceof Modifier m && m.isPrivate()) {
                edits.add(dropModifier(text, m));
              }
            }
            widened.put(body.getStartPosition(), label(d, key, outerKey, outerLabel, simple));
          }
        });
  }

  /** Whether {@code n} names something without a qualifier before it. */
  private static boolean unqualified(SimpleName n) {
    StructuralPropertyDescriptor where = n.getLocationInParent();
    if (where == MethodInvocation.NAME_PROPERTY) {
      return ((MethodInvocation) n.getParent()).getExpression() == null;
    }
    return where != QualifiedName.NAME_PROPERTY
        && where != FieldAccess.NAME_PROPERTY
        && where != SuperFieldAccess.NAME_PROPERTY
        && where != SuperMethodInvocation.NAME_PROPERTY
        && where != QualifiedType.NAME_PROPERTY
        && where != ExpressionMethodReference.NAME_PROPERTY
        && where != TypeMethodReference.NAME_PROPERTY
        && where != SuperMethodReference.NAME_PROPERTY;
  }

  /**
   * Whether {@code b} is a static method or field, or a member type other than the moved one, of
   * the outer type: what the moved type reaches without a qualifier only while it is nested.
   */
  private static boolean memberOf(IBinding b, String outerKey, String key) {
    if (b instanceof IMethodBinding m) {
      return Modifier.isStatic(m.getModifiers()) && declaredBy(m.getDeclaringClass(), outerKey);
    }
    if (b instanceof IVariableBinding v) {
      return v.isField()
          && Modifier.isStatic(v.getModifiers())
          && declaredBy(v.getDeclaringClass(), outerKey);
    }
    if (b instanceof ITypeBinding t) {
      return t.getDeclaringClass() != null
          && declaredBy(t.getDeclaringClass(), outerKey)
          && !t.getTypeDeclaration().getKey().equals(key);
    }
    return false;
  }

  private static boolean declaredBy(ITypeBinding type, String key) {
    return type != null && type.getTypeDeclaration().getKey().equals(key);
  }

  /** "Shop.label(String)", "Line.price": a widened member, its type named as after the move. */
  private static String label(
      IBinding d, String key, String outerKey, String outerLabel, String simple) {
    ITypeBinding owner =
        d instanceof IMethodBinding m
            ? m.getDeclaringClass()
            : d instanceof IVariableBinding v
                ? v.getDeclaringClass()
                : ((ITypeBinding) d).getDeclaringClass();
    String in =
        declaredBy(owner, key)
            ? simple
            : declaredBy(owner, outerKey) ? outerLabel : owner.getName();
    if (d instanceof IMethodBinding m) {
      List<String> types = new ArrayList<>();
      for (ITypeBinding t : m.getParameterTypes()) {
        types.add(t.getErasure().getName());
      }
      String params = "(" + String.join(", ", types) + ")";
      return m.isConstructor() ? in + params : in + "." + m.getName() + params;
    }
    return in + "." + d.getName();
  }

  /** The names used in {@code u} inside and outside {@code start}-{@code end}, imports left out. */
  private static void identifiers(
      CompilationUnit u, int start, int end, Set<String> inside, Set<String> outside) {
    u.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(PackageDeclaration p) {
            return false;
          }

          @Override
          public boolean visit(ImportDeclaration i) {
            return false;
          }

          @Override
          public boolean visit(SimpleName n) {
            boolean in = n.getStartPosition() >= start && n.getStartPosition() < end;
            (in ? inside : outside).add(n.getIdentifier());
            return true;
          }
        });
  }

  /** Whether {@code u} imports {@code name} already. */
  private static boolean imports(CompilationUnit u, String name) {
    for (Object o : u.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      if (!i.isStatic() && i.getName().getFullyQualifiedName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /** An import of {@code name}, in order among {@code u}'s non-static imports. */
  private static Edit importEdit(String text, CompilationUnit u, String name) {
    String line = "import " + name + ";\n";
    ImportDeclaration last = null;
    for (Object o : u.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      if (i.isStatic()) {
        continue;
      }
      if (i.getName().getFullyQualifiedName().compareTo(name) > 0) {
        int at = Lines.start(text, i.getStartPosition());
        return new Edit(at, at, line);
      }
      last = i;
    }
    if (last != null) {
      int at = Lines.end(text, last.getStartPosition());
      return new Edit(at, at, line);
    }
    int at =
        u.getPackage() == null
            ? 0
            : Lines.end(text, u.getPackage().getStartPosition() + u.getPackage().getLength());
    return new Edit(at, at, "\n" + line);
  }

  /** Removes modifier {@code m} and the spaces after it. */
  private static Edit dropModifier(String text, Modifier m) {
    int end = m.getStartPosition() + m.getLength();
    while (end < text.length() && text.charAt(end) == ' ') {
      end++;
    }
    return new Edit(m.getStartPosition(), end, "");
  }

  /** {@code text}, which starts at {@code offset} of the file, with {@code edits} applied. */
  private static String apply(String text, int offset, List<Edit> edits) {
    List<Edit> sorted = new ArrayList<>(edits);
    sorted.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end).reversed());
    StringBuilder out = new StringBuilder(text);
    for (Edit e : sorted) {
      out.replace(e.start() - offset, e.end() - offset, e.text());
    }
    return out.toString();
  }

  /**
   * {@code moved} with the indentation of the declaration's first line ({@code start} in {@code
   * text}) taken off every line.
   */
  private static String dedent(String moved, String text, int start) {
    String indent = text.substring(Lines.start(text, start), start);
    if (!indent.isBlank() && !indent.isEmpty()) {
      return moved;
    }
    List<String> out = new ArrayList<>();
    for (String line : moved.split("\n", -1)) {
      out.add(line.startsWith(indent) ? line.substring(indent.length()) : line);
    }
    return String.join("\n", out);
  }

  private static String packageOf(CompilationUnit u) {
    return u.getPackage() == null ? "" : u.getPackage().getName().getFullyQualifiedName();
  }

  private static String qualified(String pkg, String simple) {
    return pkg.isEmpty() ? simple : pkg + "." + simple;
  }

  private static String simpleName(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  private static String plural(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  private static String joinAnd(List<String> items) {
    if (items.size() == 1) {
      return items.get(0);
    }
    return String.join(", ", items.subList(0, items.size() - 1))
        + " and "
        + items.get(items.size() - 1);
  }
}
