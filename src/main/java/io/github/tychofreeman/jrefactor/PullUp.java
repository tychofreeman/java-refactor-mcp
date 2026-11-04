package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * pull_up: a method moves from its class to a direct supertype in the project. The supertype is the
 * superclass unless {@code to} names another direct supertype. The gate refuses a move that leaves
 * a name unresolved, so a method that uses what only the subclass has is not moved.
 */
final class PullUp {

  private PullUp() {}

  static Outcome run(SourceProject project, String rel, String name, String to) throws IOException {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      throw new Refusal("name is Sub.method, the class and the method to move, not " + name + ".");
    }
    String subName = name.substring(0, dot);
    String method = name.substring(dot + 1);
    Path file = project.javaFile(rel);
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    TypeDeclaration sub = typeNamed(unit, subName);
    if (sub == null || sub.resolveBinding() == null) {
      throw new Refusal(
          subName + " is not a top-level class of " + rel + " that resolves. Fix it first.");
    }
    ITypeBinding superType = supertype(sub.resolveBinding(), to);
    List<MethodDeclaration> named = new ArrayList<>();
    for (MethodDeclaration m : sub.getMethods()) {
      if (m.getName().getIdentifier().equals(method)) {
        named.add(m);
      }
    }
    if (named.isEmpty()) {
      throw new Refusal(subName + " declares no method " + method + " in " + rel + ".");
    }
    if (named.size() > 1) {
      throw new Refusal(
          subName
              + " has several methods named "
              + method
              + "; pull_up moves one method, and overloads are not supported yet.");
    }
    MethodDeclaration moved = named.get(0);

    Holding holding = Holding.of(project, superType.getErasure().getName(), file);
    Path target = null;
    TypeDeclaration superDecl = null;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      TypeDeclaration d = typeNamed(e.getValue(), superType.getErasure().getName());
      if (d != null
          && d.resolveBinding() != null
          && d.resolveBinding()
              .getErasure()
              .getQualifiedName()
              .equals(superType.getErasure().getQualifiedName())) {
        target = e.getKey();
        superDecl = d;
      }
    }
    if (target == null) {
      throw new Refusal(
          superType.getErasure().getName()
              + " is not a top-level class or interface of the project's sources, so "
              + method
              + " cannot be pulled into it.");
    }
    for (MethodDeclaration m : superDecl.getMethods()) {
      if (m.getName().getIdentifier().equals(method)) {
        throw new Refusal(
            superType.getErasure().getName()
                + " already declares "
                + method
                + ". Rename one of them, or pull_up is not the change you want.");
      }
    }

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Map<Path, String> texts = holding.texts();
    String movedText = text.substring(moved.getStartPosition(), end(moved));
    edits
        .computeIfAbsent(file, k -> new ArrayList<>())
        .add(
            new TextEdit(
                Lines.start(text, moved.getStartPosition()), Lines.end(text, end(moved) - 1), ""));
    String targetText = texts.getOrDefault(target, Files.readString(target));
    int brace = end(superDecl) - 1;
    int at = Lines.start(targetText, brace);
    edits
        .computeIfAbsent(target, k -> new ArrayList<>())
        .add(new TextEdit(at, at, "\n  " + movedText + "\n"));

    Map<Path, String> changed = new LinkedHashMap<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      String before = texts.getOrDefault(e.getKey(), Files.readString(e.getKey()));
      List<TextEdit> list = new ArrayList<>(e.getValue());
      list.sort((a, b) -> b.start() - a.start());
      String after = TextEdit.apply(before, list, 0);
      changed.put(e.getKey(), Imports.fix(project, e.getKey(), before, after, List.of()).text());
    }
    Stubs.Declared declared =
        Gate.applyOrRefuse(
            project,
            changed,
            "pulling " + subName + "." + method + " up to " + superType.getErasure().getName(),
            "Nothing was changed. Move only a method that uses nothing the subclass has.");
    return new Outcome(
        "pulled "
            + subName
            + "."
            + method
            + " up to "
            + superType.getErasure().getName()
            + " ("
            + project.relative(target)
            + ")",
        declared);
  }

  /**
   * The superclass, or the direct supertype named {@code to}; refused when there is no such one.
   */
  private static ITypeBinding supertype(ITypeBinding sub, String to) {
    ITypeBinding superclass = sub.getSuperclass();
    boolean hasSuperclass =
        superclass != null && !"java.lang.Object".equals(superclass.getQualifiedName());
    if (to == null) {
      if (!hasSuperclass) {
        throw new Refusal(
            sub.getName()
                + " has no superclass to pull up into. Pass to, a direct supertype: "
                + directNames(sub)
                + ".");
      }
      return superclass;
    }
    if (hasSuperclass && superclass.getErasure().getName().equals(to)) {
      return superclass;
    }
    for (ITypeBinding i : sub.getInterfaces()) {
      if (i.getErasure().getName().equals(to)) {
        return i;
      }
    }
    throw new Refusal(
        to
            + " is not a direct supertype of "
            + sub.getName()
            + ". Its direct supertypes are "
            + directNames(sub)
            + ".");
  }

  private static String directNames(ITypeBinding sub) {
    List<String> names = new ArrayList<>();
    ITypeBinding superclass = sub.getSuperclass();
    if (superclass != null && !"java.lang.Object".equals(superclass.getQualifiedName())) {
      names.add(superclass.getErasure().getName());
    }
    for (ITypeBinding i : sub.getInterfaces()) {
      names.add(i.getErasure().getName());
    }
    return names.isEmpty() ? "none" : String.join(", ", names);
  }

  /** The top-level type of {@code unit} named {@code name}, or null. */
  private static TypeDeclaration typeNamed(CompilationUnit unit, String name) {
    for (Object o : unit.types()) {
      if (o instanceof TypeDeclaration t && t.getName().getIdentifier().equals(name)) {
        return t;
      }
    }
    return null;
  }

  private static int end(org.eclipse.jdt.core.dom.ASTNode n) {
    return n.getStartPosition() + n.getLength();
  }
}
