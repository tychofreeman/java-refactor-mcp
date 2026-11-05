package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * extract_supertype: a new interface or superclass from members of a class, in a file of its own in
 * the class's package. An interface takes the signatures of public instance methods, and the class
 * implements it; a superclass takes the members themselves, moved by {@link PullUp}, and takes the
 * class's place in its {@code extends}. One call, so a refused step rolls back them all.
 */
final class ExtractSupertype {

  private ExtractSupertype() {}

  static Outcome run(
      SourceProject project,
      String rel,
      String name,
      String supertype,
      String kind,
      List<String> members)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> "interface".equals(kind) || "class".equals(kind),
            () -> "kind is interface or class, the supertype to extract."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(supertype) && !SourceVersion.isKeyword(supertype),
            () -> supertype + " is not a valid name for a type."),
        Precondition.that(
            () -> !members.isEmpty(),
            () -> "members names the methods (and, for a class, fields) to extract."));
    Opened opened = Opened.of(project, rel);
    TypeDeclaration type = opened.topLevel(name);
    Path target = opened.file().resolveSibling(supertype + ".java");
    Precondition.require(
        Precondition.that(
            () -> !type.isInterface(),
            () -> name + " is an interface; extract_supertype extracts from a class."),
        Precondition.that(
            () -> type.typeParameters().isEmpty(),
            () -> name + " is generic, which extract_supertype does not handle yet."),
        Precondition.that(
            () -> !Files.exists(target),
            () -> project.relative(target) + " already exists. Choose another name."));
    String pkg =
        opened.unit().getPackage() == null
            ? ""
            : "package " + opened.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String text = opened.text();
    if (kind.equals("interface")) {
      List<String> signatures = new ArrayList<>();
      for (String member : members) {
        BodyDeclaration d = MemberMove.member(type, name, member, rel, "extract_supertype");
        Precondition.require(
            Precondition.that(
                () ->
                    d instanceof MethodDeclaration m
                        && !m.isConstructor()
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers()),
                () ->
                    name
                        + "."
                        + member
                        + " is not a public instance method, which an interface can declare. Make it"
                        + " public first, or leave it out."));
        signatures.add(signature(text, (MethodDeclaration) d));
      }
      StringBuilder body = new StringBuilder(pkg + "public interface " + supertype + " {\n");
      for (String s : signatures) {
        body.append("  ").append(s).append(";\n");
      }
      body.append("}\n");
      create(project, target, body.toString(), supertype);
      AddSupertype.run(project, rel, name, "implements", supertype);
      return Outcome.of(
          "extracted interface "
              + supertype
              + " ("
              + project.relative(target)
              + ") from "
              + name
              + " with "
              + Extract.and(members)
              + "; "
              + name
              + " implements it");
    }

    Type superclass = type.getSuperclassType();
    String extending =
        superclass == null
            ? ""
            : " extends "
                + text.substring(
                    superclass.getStartPosition(),
                    superclass.getStartPosition() + superclass.getLength());
    create(project, target, pkg + "public class " + supertype + extending + " {\n}\n", supertype);
    if (superclass == null) {
      AddSupertype.run(project, rel, name, "extends", supertype);
    } else {
      String changed =
          text.substring(0, superclass.getStartPosition())
              + supertype
              + text.substring(superclass.getStartPosition() + superclass.getLength());
      Gate.applyOrRefuse(
          project,
          Map.of(opened.file(), changed),
          "making " + name + " extend " + supertype,
          "Nothing was changed.");
    }
    for (String member : members) {
      PullUp.run(project, rel, name + "." + member, supertype);
    }
    return Outcome.of(
        "extracted class "
            + supertype
            + " ("
            + project.relative(target)
            + ") from "
            + name
            + " with "
            + Extract.and(members)
            + "; "
            + name
            + " extends it");
  }

  /** Writes the new type's file, its imports fixed, through the gate. */
  private static void create(SourceProject project, Path target, String text, String supertype)
      throws Exception {
    String fixed = Imports.fix(project, target, "", text, List.of()).text();
    Gate.applyOrRefuse(
        project,
        Map.of(target, fixed),
        "creating " + supertype,
        "Nothing was changed; name the types its members use in their file first.");
  }

  /** {@code m}'s signature as written: from after its modifiers to its body, without them. */
  private static String signature(String text, MethodDeclaration m) {
    int start = m.getJavadoc() != null ? MemberMove.end(m.getJavadoc()) : m.getStartPosition();
    for (Object o : m.modifiers()) {
      start = Math.max(start, MemberMove.end((ASTNode) o));
    }
    int end = m.getBody() != null ? m.getBody().getStartPosition() : MemberMove.end(m);
    return text.substring(start, end).strip();
  }
}
