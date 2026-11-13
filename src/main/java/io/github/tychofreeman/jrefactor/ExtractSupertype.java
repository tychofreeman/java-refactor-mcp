package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;

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
            () -> typeParameter(type, supertype) == null,
            () ->
                supertype
                    + " is a type parameter of "
                    + name
                    + ": in "
                    + name
                    + "'s header the name would mean it, not the new "
                    + kind
                    + ". Choose another name."),
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
      List<ASTNode> uses = new ArrayList<>();
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
        MethodDeclaration m = (MethodDeclaration) d;
        signatures.add(signature(text, m));
        uses.addAll(Nodes.all(m, SingleVariableDeclaration.class));
        uses.addAll(Nodes.all(m, TypeParameter.class));
        uses.addAll(Nodes.all(m, Type.class).stream().filter(t -> t.getParent() == m).toList());
      }
      Generics generics = generics(type, text, uses, supertype);
      StringBuilder body =
          new StringBuilder(pkg + "public interface " + supertype + generics.declared() + " {\n");
      for (String s : signatures) {
        body.append("  ").append(s).append(";\n");
      }
      body.append("}\n");
      create(project, target, body.toString(), supertype);
      AddSupertype.run(project, rel, name, "implements", supertype + generics.named());
      return Outcome.of(
          "extracted interface "
              + supertype
              + generics.named()
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
    List<ASTNode> uses = new ArrayList<>();
    for (String member : members) {
      uses.add(MemberMove.member(type, name, member, rel, "extract_supertype"));
    }
    if (superclass != null) {
      uses.add(superclass);
    }
    Generics generics = generics(type, text, uses, supertype);
    create(
        project,
        target,
        pkg + "public class " + supertype + generics.declared() + extending + " {\n}\n",
        supertype);
    if (superclass == null) {
      AddSupertype.run(project, rel, name, "extends", supertype + generics.named());
    } else {
      String changed =
          text.substring(0, superclass.getStartPosition())
              + supertype
              + generics.named()
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
            + generics.named()
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
    int start = m.getJavadoc() != null ? Nodes.end(m.getJavadoc()) : m.getStartPosition();
    for (Object o : m.modifiers()) {
      start = Math.max(start, Nodes.end(((ASTNode) o)));
    }
    int end = m.getBody() != null ? m.getBody().getStartPosition() : Nodes.end(m);
    return text.substring(start, end).strip();
  }

  /**
   * The type parameters of a class that a type extracted from it declares: {@code declared} as the
   * class writes them, bounds included ({@code <K extends Comparable<K>, V>}), and {@code named},
   * the type arguments the class passes ({@code <K, V>}). Both empty when it needs none.
   */
  private record Generics(String declared, String named) {}

  /** The type parameter of {@code type} named {@code name}, or null. */
  private static TypeParameter typeParameter(TypeDeclaration type, String name) {
    for (Object o : type.typeParameters()) {
      if (((TypeParameter) o).getName().getIdentifier().equals(name)) {
        return (TypeParameter) o;
      }
    }
    return null;
  }

  /**
   * The type parameters of {@code type} that {@code uses} (signatures for an interface; whole
   * members and the old superclass's type for a class) name, found by binding, with those their
   * bounds name, in the class's order. A bound is copied exactly, so the new type's methods erase
   * to what the class's do; one that names a type {@code supertype}'s file cannot is refused.
   */
  private static Generics generics(
      TypeDeclaration type, String text, List<ASTNode> uses, String supertype) {
    List<TypeParameter> used = new ArrayList<>();
    List<ASTNode> pending = new ArrayList<>(uses);
    while (!pending.isEmpty()) {
      for (SimpleName n : Nodes.all(pending.remove(0), SimpleName.class)) {
        if (!(n.resolveBinding() instanceof ITypeBinding b)) {
          continue;
        }
        TypeParameter p = b.isTypeVariable() ? typeParameter(type, b.getName()) : null;
        if (p != null && b.isEqualTo(p.resolveBinding()) && !used.contains(p)) {
          used.add(p);
          for (Object bound : p.typeBounds()) {
            pending.add((ASTNode) bound);
          }
        }
      }
    }
    List<String> declared = new ArrayList<>();
    List<String> named = new ArrayList<>();
    for (Object o : type.typeParameters()) {
      TypeParameter p = (TypeParameter) o;
      if (!used.contains(p)) {
        continue;
      }
      for (Object bound : p.typeBounds()) {
        for (SimpleName n : Nodes.all((ASTNode) bound, SimpleName.class)) {
          for (ITypeBinding t = n.resolveBinding() instanceof ITypeBinding b ? b : null;
              t != null;
              t = t.getDeclaringClass()) {
            if (Modifier.isPrivate(t.getModifiers())) {
              throw new Refusal(
                  "the bound of "
                      + p.getName().getIdentifier()
                      + " uses the private type "
                      + Names.typeLabel(t)
                      + ", which "
                      + supertype
                      + ", in its own file, cannot name. Make "
                      + Names.typeLabel(t)
                      + " package-private or move it to its own file first (move).");
            }
          }
        }
      }
      declared.add(text.substring(p.getStartPosition(), Nodes.end(p)));
      named.add(p.getName().getIdentifier());
    }
    return declared.isEmpty()
        ? new Generics("", "")
        : new Generics(
            "<" + String.join(", ", declared) + ">", "<" + String.join(", ", named) + ">");
  }
}
