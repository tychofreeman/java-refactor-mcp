package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
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
 * moved. A method the supertype declares abstract takes the place of that declaration; the
 * supertype's other subclasses keep their own.
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
    MethodDeclaration twin = abstractTwin(superDecl, moved);
    if (twin == null && MemberMove.declares(superDecl, moved)) {
      throw new Refusal(
          superName
              + " already declares "
              + member
              + ". Rename one of them, or pull_up is not the change you want.");
    }
    if (moved instanceof MethodDeclaration md && md.resolveBinding() != null) {
      IMethodBinding inherited = Hierarchy.concreteOverridden(md.resolveBinding(), superType);
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
    if (twin != null) {
      edits
          .computeIfAbsent(target, k -> new ArrayList<>())
          .add(implement(targetText, twin, text, (MethodDeclaration) moved));
    } else {
      edits
          .computeIfAbsent(target, k -> new ArrayList<>())
          .addAll(MemberMove.addTo(targetText, superDecl, movedText, Placement.rank(moved)));
    }

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
            + ")"
            + (twin == null
                ? ""
                : ", in place of its abstract declaration" + stillAbstract(superDecl)),
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

  /**
   * The abstract method of {@code type} that {@code moved} implements (same name and parameter
   * types), or null.
   */
  private static MethodDeclaration abstractTwin(TypeDeclaration type, BodyDeclaration moved) {
    if (!(moved instanceof MethodDeclaration m) || m.resolveBinding() == null) {
      return null;
    }
    for (MethodDeclaration d : type.getMethods()) {
      if (Modifier.isAbstract(d.getModifiers())
          && d.resolveBinding() != null
          && d.getName().getIdentifier().equals(m.getName().getIdentifier())
          && MemberMove.erasedParams(d.resolveBinding())
              .equals(MemberMove.erasedParams(m.resolveBinding()))) {
        return d;
      }
    }
    return null;
  }

  /**
   * The edit that replaces {@code twin}, abstract in {@code targetText}, with {@code moved} from
   * {@code text}: twin's Javadoc stays in place of moved's, and moved's {@code @Override} goes
   * unless twin has one too.
   */
  private static TextEdit implement(
      String targetText, MethodDeclaration twin, String text, MethodDeclaration moved) {
    int at =
        moved.getJavadoc() != null && twin.getJavadoc() != null
            ? after(text, moved.getJavadoc())
            : moved.getStartPosition();
    StringBuilder written = new StringBuilder();
    if (twin.getJavadoc() != null) {
      // From the declaration's start, so that the method declared is written by this edit.
      written.append(targetText, twin.getStartPosition(), after(targetText, twin.getJavadoc()));
    }
    if (override(twin) == null && override(moved) instanceof Annotation a) {
      written.append(text, at, a.getStartPosition());
      at = after(text, a);
    }
    written.append(text, at, Nodes.end(moved));
    return new TextEdit(twin.getStartPosition(), Nodes.end(twin), written.toString());
  }

  /** {@code m}'s {@code @Override}, or null. */
  private static Annotation override(MethodDeclaration m) {
    for (Object o : m.modifiers()) {
      if (o instanceof Annotation a
          && a.getTypeName().getFullyQualifiedName().endsWith("Override")) {
        return a;
      }
    }
    return null;
  }

  /** The offset of the first character after {@code node} that is not white space. */
  private static int after(String text, ASTNode node) {
    int i = Nodes.end(node);
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * "; Renamer is still abstract, and nothing else in it is: it could be made a concrete class"
   * when that is so, for the result; otherwise empty.
   */
  private static String stillAbstract(TypeDeclaration type) {
    if (!Modifier.isAbstract(type.getModifiers()) || type.isInterface()) {
      return "";
    }
    long others =
        List.of(type.getMethods()).stream()
            .filter(d -> Modifier.isAbstract(d.getModifiers()))
            .count();
    String name = type.getName().getIdentifier();
    return others > 1
        ? ""
        : "; "
            + name
            + " is still abstract, and nothing else in it is: it could be made a concrete class";
  }
}
