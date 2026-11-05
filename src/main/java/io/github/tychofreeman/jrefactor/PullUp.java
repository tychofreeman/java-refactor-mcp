package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * pull_up: a method or a field moves from its class to a direct supertype in the project. The
 * supertype is the superclass unless {@code to} names another direct supertype. The gate refuses a
 * move that leaves a name unresolved, so a member that uses what only the subclass has is not
 * moved.
 */
final class PullUp {

  private PullUp() {}

  static Outcome run(SourceProject project, String rel, String name, String to) throws IOException {
    Qualified q = Qualified.of(name, "Sub.member, the class and the method or field to move");
    String subName = q.type();
    String member = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    TypeDeclaration sub = opened.topLevel(subName);
    ITypeBinding superType = supertype(sub.resolveBinding(), to);
    BodyDeclaration moved = MemberMove.member(sub, subName, member, rel, "pull_up");

    String superName = superType.getErasure().getName();
    Holding holding = Holding.of(project, superName, file);
    Path target = null;
    TypeDeclaration superDecl = null;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      TypeDeclaration d = MemberMove.typeNamed(e.getValue(), superName);
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
          superName
              + " is not a top-level class or interface of the project's sources, so "
              + member
              + " cannot be pulled into it.");
    }
    if (MemberMove.declares(superDecl, moved)) {
      throw new Refusal(
          superName
              + " already declares "
              + member
              + ". Rename one of them, or pull_up is not the change you want.");
    }
    if (moved instanceof MethodDeclaration md && md.resolveBinding() != null) {
      IMethodBinding inherited = overridden(md.resolveBinding(), superType);
      if (inherited != null) {
        String declaring = inherited.getDeclaringClass().getErasure().getName();
        throw new Refusal(
            subName
                + "."
                + member
                + " overrides "
                + declaring
                + "."
                + MemberMove.label(md)
                + ", which "
                + superName
                + " inherits: in "
                + superName
                + " it would replace that one for "
                + superName
                + " and its other subclasses too. Keep it in "
                + subName
                + ", or pull it up to "
                + declaring
                + " if every class below should run it.");
      }
    }

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Map<Path, String> texts = holding.texts();
    String movedText = MemberMove.textOf(text, moved);
    edits.computeIfAbsent(file, k -> new ArrayList<>()).add(MemberMove.removal(text, moved));
    String targetText = texts.getOrDefault(target, Files.readString(target));
    edits
        .computeIfAbsent(target, k -> new ArrayList<>())
        .addAll(MemberMove.appendTo(targetText, superDecl, movedText));

    NameCheck.require(
        project, texts, edits, NameCheck.meanings(List.of(moved)), "pulling " + name + " up");
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "pulling " + subName + "." + member + " up to " + superName,
            "Nothing was changed. Move only a member that uses nothing the subclass has.");
    return new Outcome(
        "pulled "
            + subName
            + "."
            + member
            + " up to "
            + superName
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

  /**
   * The first concrete method of {@code type}'s supertypes (superclasses and interfaces, not {@code
   * type} itself) that {@code method} overrides, or null. In {@code type}, the moved method would
   * take its place for every class below that does not declare its own.
   */
  private static IMethodBinding overridden(IMethodBinding method, ITypeBinding type) {
    List<ITypeBinding> todo = new ArrayList<>();
    if (type.getSuperclass() != null) {
      todo.add(type.getSuperclass());
    }
    todo.addAll(List.of(type.getInterfaces()));
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding t = todo.get(i);
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (!Modifier.isAbstract(m.getModifiers()) && method.overrides(m)) {
          return m;
        }
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return null;
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
}
