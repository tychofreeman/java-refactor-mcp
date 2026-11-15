package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;

/**
 * Moves a static member type into another top-level class of its package, as a member type there:
 * every reference follows (Outer.Inner becomes Target.Inner, and Inner inside Target), its uses of
 * the outer type's static members are qualified, and private members used across either boundary
 * become package-private, as {@link MoveType} does for a move to its own file.
 */
final class NestType {

  private NestType() {}

  /** Moves the member type {@code name} ("Outer.Inner") declared in {@code rel} into {@code to}. */
  static Outcome run(SourceProject project, String rel, String name, String to) throws IOException {
    Path file = project.javaFile(rel);
    String simple = Names.simpleName(name);
    String pkg = Names.packageOf(Jdt.parseSyntax(Edits.textOf(Map.of(), file)));
    Path targetFile = targetFile(project, pkg, to, name);
    String toSimple = Names.simpleName(to);
    if (targetFile.equals(file)) {
      throw new Refusal(
          to
              + " is declared in "
              + rel
              + ", the file of "
              + name
              + ". move takes a member type into a class of another file so far.");
    }
    Holding holding = Holding.of(project, simple, file, targetFile);
    Map<Path, String> held = holding.texts();
    Map<Path, CompilationUnit> parsed = holding.units();
    String source = held.get(file);
    CompilationUnit unit = parsed.get(file);
    AbstractTypeDeclaration decl = MoveType.find(unit, name, rel);
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
    String targetText = held.get(targetFile);
    CompilationUnit targetUnit = parsed.get(targetFile);
    AbstractTypeDeclaration target = topLevel(targetUnit, toSimple);
    if (target == null) {
      throw new Refusal(
          "No top-level class " + toSimple + " in " + project.relative(targetFile) + ".");
    }
    if ((target instanceof TypeDeclaration td && td.isInterface())
        || target instanceof AnnotationTypeDeclaration) {
      throw new Refusal(
          toSimple
              + " is an interface, whose member types are public. move takes a member type into a"
              + " class, record or enum so far.");
    }
    if (toSimple.equals(simple)) {
      throw new Refusal(
          name
              + " would be a member type of "
              + toSimple
              + ", its own name, which Java does not allow. Rename "
              + name
              + " first.");
    }
    ITypeBinding targetType = target.resolveBinding();
    if (targetType == null) {
      throw new Refusal(
          toSimple
              + " does not resolve. Fix the compile errors in "
              + project.relative(targetFile)
              + " first.");
    }
    clash(targetType, target, simple, toSimple, name);
    String key = type.getTypeDeclaration().getKey();
    String targetKey = targetType.getErasure().getKey();
    hides(held, parsed, project, key, targetKey, simple, toSimple, name);

    String outerKey = type.getDeclaringClass().getTypeDeclaration().getKey();
    int start = decl.getStartPosition();
    int end = start + decl.getLength();
    MovedType moved =
        new MovedType(start, end, pkg, name, simple, key, outerKey, outerLabel, toSimple);
    String nested = toSimple + "." + simple;
    String qualifiedNested = (pkg.isEmpty() ? "" : pkg + ".") + nested;
    int targetStart = target.getStartPosition();
    int targetEnd = targetStart + target.getLength();

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Map<String, List<Integer>> lines = new TreeMap<>();
    int references = 0;
    boolean usedOutside = false;
    TreeMap<Integer, String> widened = new TreeMap<>();
    Set<String> accessed = new TreeSet<>();
    List<String> sealed = new ArrayList<>();
    List<TextEdit> inside = new ArrayList<>();
    for (Map.Entry<Path, String> e : held.entrySet()) {
      Path f = e.getKey();
      String text = e.getValue();
      CompilationUnit u = parsed.get(f);
      List<TextEdit> fileEdits = new ArrayList<>();
      List<ASTNode> refs = new ArrayList<>();
      boolean isOuter = f.equals(file);
      boolean isTarget = f.equals(targetFile);
      references(
          u,
          text,
          key,
          at -> (isOuter && moved.inside(at)) || (isTarget && at >= targetStart && at < targetEnd),
          nested,
          qualifiedNested,
          fileEdits,
          refs);
      for (ASTNode r : refs) {
        if (!(isOuter && moved.inside(r.getStartPosition()))) {
          usedOutside |=
              !(isTarget
                  && r.getStartPosition() >= targetStart
                  && r.getStartPosition() < targetEnd);
        }
      }
      List<ASTNode> counted =
          refs.stream().filter(r -> !(isOuter && moved.inside(r.getStartPosition()))).toList();
      if (!counted.isEmpty()) {
        Set<Integer> at = new TreeSet<>();
        for (ASTNode r : counted) {
          at.add(u.getLineNumber(r.getStartPosition()));
        }
        lines.put(project.relative(f).toString(), new ArrayList<>(at));
        references += counted.size();
      }
      if (isOuter) {
        MoveType.qualifyAndWiden(
            u, text, decl, type, moved, fileEdits, widened, accessed, new TreeSet<>());
        MoveType.permits(u, text, moved, fileEdits, widened, sealed);
        List<TextEdit> outside = new ArrayList<>();
        for (TextEdit edit : fileEdits) {
          (moved.inside(edit.start()) ? inside : outside).add(edit);
        }
        outside.add(MemberMove.removal(text, decl));
        edits.put(f, outside);
      } else {
        edits.put(f, fileEdits);
      }
    }
    if (usedOutside) {
      for (Object o : decl.modifiers()) {
        if (o instanceof Modifier m && m.isPrivate()) {
          inside.add(MoveType.dropModifier(source, m));
          widened.put(start, simple);
        }
      }
    }
    String movedText = TextEdit.apply(source.substring(start, end), inside, start);
    List<TextEdit> added = MemberMove.addTo(targetText, target, movedText, Placement.Rank.OTHER);
    NameCheck.require(
        project,
        Map.of(targetFile, targetText),
        Map.of(targetFile, added),
        NameCheck.meanings(List.of(decl)),
        "moving " + name + " into " + toSimple);
    List<TextEdit> targetEdits = new ArrayList<>(edits.get(targetFile));
    targetEdits.addAll(added);
    String targetAfter = withImports(TextEdit.apply(targetText, targetEdits, 0), unit, moved);
    edits.put(targetFile, List.of(new TextEdit(0, targetText.length(), targetAfter)));
    List<String> chosen = new ArrayList<>();
    chosen.add((pkg.isEmpty() ? "" : pkg + ".") + toSimple);
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            held,
            edits,
            chosen,
            "moving " + name + " into " + toSimple,
            "Fix what the errors name, then move it again; when a name clashes, rename one of"
                + " them first.");
    String result = "moved " + name + " into " + project.relative(targetFile) + " as " + nested;
    if (references > 0) {
      List<String> each = new ArrayList<>();
      lines.forEach((r, ls) -> each.add(r + " " + Lines.list(ls)));
      result +=
          ", with "
              + Names.plural(references, "reference")
              + " in "
              + Names.plural(lines.size(), "file")
              + ": "
              + String.join("; ", each);
    }
    if (!widened.isEmpty()) {
      result +=
          "; widened " + Names.listed(new ArrayList<>(widened.values())) + " to package-private";
    }
    if (!accessed.isEmpty()) {
      result += "; reads of " + Names.listed(new ArrayList<>(accessed)) + " became accessor calls";
    }
    if (!sealed.isEmpty()) {
      result +=
          "; wrote a permits clause on "
              + Names.listed(sealed)
              + ", as the move put permitted subtypes in another file";
    }
    return new Outcome(result + written.said(), written.declared());
  }

  /**
   * The file of the top-level class {@code to} (simple, of package {@code pkg}, or qualified), or a
   * refusal: none, or one in another package.
   */
  private static Path targetFile(SourceProject project, String pkg, String to, String name) {
    String own = pkg.isEmpty() ? to : pkg + "." + to;
    Path f = project.fileDeclaring(own);
    if (f != null) {
      return f;
    }
    if (to.contains(".") && project.fileDeclaring(to) != null) {
      if (to.substring(0, to.lastIndexOf('.')).equals(pkg)) {
        return project.fileDeclaring(to);
      }
      throw new Refusal(
          to
              + " is in another package than "
              + name
              + ". move takes a member type into a class of its own package so far; to move it to"
              + " another package, move it to its own file (move without to), then pass package.");
    }
    throw new Refusal(
        "No top-level class "
            + to
            + " in package "
            + (pkg.isEmpty() ? "(default)" : pkg)
            + ": to names a top-level class of "
            + name
            + "'s package, as Target or "
            + (pkg.isEmpty() ? "" : pkg + ".")
            + "Target.");
  }

  private static AbstractTypeDeclaration topLevel(CompilationUnit unit, String simple) {
    for (Object o : unit.types()) {
      if (o instanceof AbstractTypeDeclaration t && t.getName().getIdentifier().equals(simple)) {
        return t;
      }
    }
    return null;
  }

  /**
   * Refuses when {@code target} declares or inherits a member type named {@code simple}, or
   * declares a type parameter of that name.
   */
  private static void clash(
      ITypeBinding target,
      AbstractTypeDeclaration decl,
      String simple,
      String toSimple,
      String name) {
    for (ITypeBinding t : Hierarchy.supertypes(target, true)) {
      for (ITypeBinding member : t.getDeclaredTypes()) {
        if (member.getName().equals(simple)) {
          throw new Refusal(
              toSimple
                  + " already has a member type "
                  + simple
                  + (t == target ? "" : ", inherited from " + Names.typeLabel(t))
                  + ". Rename one of them first, then move "
                  + name
                  + ".");
        }
      }
    }
    if (decl instanceof TypeDeclaration td) {
      for (Object o : td.typeParameters()) {
        if (((TypeParameter) o).getName().getIdentifier().equals(simple)) {
          throw new Refusal(
              toSimple
                  + " has a type parameter "
                  + simple
                  + ". Rename one of them first, then move "
                  + name
                  + ".");
        }
      }
    }
  }

  /**
   * Refuses when a name {@code simple} written without a qualifier, in {@code toSimple} or a type
   * that inherits from it, means another type, which the moved type would hide there.
   */
  private static void hides(
      Map<Path, String> held,
      Map<Path, CompilationUnit> parsed,
      SourceProject project,
      String key,
      String targetKey,
      String simple,
      String toSimple,
      String name) {
    for (Path f : held.keySet()) {
      CompilationUnit u = parsed.get(f);
      for (SimpleName n : Nodes.all(u, SimpleName.class)) {
        if (!n.getIdentifier().equals(simple)
            || n.isDeclaration()
            || Nodes.qualified(n)
            || !(n.resolveBinding() instanceof ITypeBinding t)
            || t.isTypeVariable()
            || t.getTypeDeclaration().getKey().equals(key)) {
          continue;
        }
        for (ITypeBinding around = Nodes.typeAround(n);
            around != null;
            around = around.getDeclaringClass()) {
          if (Hierarchy.isSubtype(around, targetKey)) {
            throw new Refusal(
                "moving "
                    + name
                    + " into "
                    + toSimple
                    + " would hide "
                    + t.getTypeDeclaration().getQualifiedName()
                    + ", which "
                    + simple
                    + " means at line "
                    + u.getLineNumber(n.getStartPosition())
                    + " of "
                    + project.relative(f)
                    + ". Rename one of them, or qualify that use, first.");
          }
        }
      }
    }
  }

  /**
   * Adds to {@code edits} the changes that name the moved type (key {@code key}) where it is after
   * the move: its simple name inside the target, {@code nested} (Target.Inner) elsewhere, and in
   * imports {@code qualifiedNested}; and to {@code refs} each reference changed or kept.
   */
  private static void references(
      CompilationUnit u,
      String text,
      String key,
      IntPredicate within,
      String nested,
      String qualifiedNested,
      List<TextEdit> edits,
      List<ASTNode> refs) {
    boolean[] imported = {false};
    for (Object o : u.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      if (!i.isOnDemand() && MoveType.declaredBy(typeOf(i.getName()), key)) {
        imported[0] = true;
      }
    }
    u.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(PackageDeclaration p) {
            return false;
          }

          @Override
          public boolean visit(ImportDeclaration i) {
            for (Name n = i.getName(); n != null; ) {
              if (MoveType.declaredBy(typeOf(n), key)) {
                refs.add(i);
                edits.add(new TextEdit(n.getStartPosition(), Nodes.end(n), qualifiedNested));
                break;
              }
              n = n instanceof QualifiedName q ? q.getQualifier() : null;
            }
            return false;
          }

          @Override
          public boolean visit(QualifiedName q) {
            if (!MoveType.declaredBy(typeOf(q), key)) {
              return true;
            }
            refs.add(q);
            String written =
                within.test(q.getStartPosition())
                    ? Names.simpleName(nested)
                    : q.getQualifier() instanceof SimpleName ? nested : qualifiedNested;
            edits.add(new TextEdit(q.getStartPosition(), Nodes.end(q), written));
            return false;
          }

          @Override
          public boolean visit(SimpleName n) {
            if (n.isDeclaration()
                || n.getLocationInParent() == QualifiedName.NAME_PROPERTY
                || !MoveType.declaredBy(typeOf(n), key)) {
              return true;
            }
            refs.add(n);
            if (!within.test(n.getStartPosition()) && !imported[0]) {
              edits.add(new TextEdit(n.getStartPosition(), Nodes.end(n), nested));
            }
            return true;
          }
        });
  }

  /** The type {@code n} names, or null when it names none. */
  private static ITypeBinding typeOf(Name n) {
    return n.resolveBinding() instanceof ITypeBinding t ? t : null;
  }

  /**
   * {@code text}, the target file's new text, with each import of the outer file ({@code unit})
   * that the moved type uses and the target does not have.
   */
  private static String withImports(String text, CompilationUnit unit, MovedType moved) {
    Set<String> used = new TreeSet<>();
    for (SimpleName n : Nodes.all(unit, SimpleName.class)) {
      if (moved.inside(n.getStartPosition())) {
        used.add(n.getIdentifier());
      }
    }
    for (Object o : unit.imports()) {
      ImportDeclaration i = (ImportDeclaration) o;
      String full = i.getName().getFullyQualifiedName();
      if (i.isOnDemand() || !used.contains(Names.simpleName(full))) {
        continue;
      }
      String name = (i.isStatic() ? "static " : "") + full;
      CompilationUnit after = Jdt.parseSyntax(text);
      boolean has = false;
      for (Object a : after.imports()) {
        ImportDeclaration ai = (ImportDeclaration) a;
        has |= ai.isStatic() == i.isStatic() && ai.getName().getFullyQualifiedName().equals(full);
      }
      if (!has) {
        text = Imports.adding(text, after, name);
      }
    }
    return text;
  }
}
