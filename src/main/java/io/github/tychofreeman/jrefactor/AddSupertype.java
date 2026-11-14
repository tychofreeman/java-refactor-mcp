package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * add_supertype: a top-level type gains a supertype in its header, as extends or implements. The
 * gate checks that the supertype resolves and that the type still compiles, so a supertype that is
 * not declared is refused and nothing is changed.
 */
final class AddSupertype {

  private AddSupertype() {}

  static Outcome run(
      SourceProject project, String rel, String name, String relation, String supertype)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> supertype != null && !supertype.isBlank(),
            () -> "add_supertype needs supertype, the supertype as written, such as Base."),
        Precondition.that(
            () -> "extends".equals(relation) || "implements".equals(relation),
            () -> "relation is extends or implements, the clause the supertype goes in."));
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    TypeDeclaration owner = opened.topLevel(name);
    boolean isInterface = owner.isInterface();
    Precondition.require(
        Precondition.that(
            () -> !isInterface || !"implements".equals(relation),
            () ->
                name
                    + " is an interface, which extends its supertypes and does not implement them."
                    + " Use relation=extends."),
        Precondition.that(
            () -> isInterface || !"extends".equals(relation) || owner.getSuperclassType() == null,
            () ->
                name
                    + " already extends "
                    + owner.getSuperclassType()
                    + ". A class has one superclass; a second supertype is an interface, added"
                    + " with relation=implements."));
    List<Type> interfaces = new ArrayList<>();
    for (Object o : owner.superInterfaceTypes()) {
      interfaces.add((Type) o);
    }

    // The new text goes into the list the type already has, after its last name; else it starts a
    // clause, before the body's brace or a permits clause (for extends with implements already
    // there, before that keyword, since a class's superclass comes first).
    int at;
    String insert;
    if (!interfaces.isEmpty() && (isInterface || "implements".equals(relation))) {
      Type last = interfaces.get(interfaces.size() - 1);
      at = Nodes.end(last);
      insert = ", " + supertype;
    } else if (!interfaces.isEmpty()) {
      at = text.lastIndexOf("implements", interfaces.get(0).getStartPosition());
      insert = "extends " + supertype + " ";
    } else {
      SimpleName nameNode = owner.getName();
      int brace = text.indexOf('{', Nodes.end(nameNode));
      at =
          owner.permittedTypes().isEmpty()
              ? brace
              : text.lastIndexOf(
                  "permits", ((ASTNode) owner.permittedTypes().get(0)).getStartPosition());
      while (at > 0 && Character.isWhitespace(text.charAt(at - 1))) {
        at--;
      }
      insert = " " + relation + " " + supertype;
    }
    String updated = text.substring(0, at) + insert + text.substring(at);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            updated,
            List.of(),
            "adding " + supertype + " as a supertype of " + name,
            "Declare the supertype first, or check its name and the type's other supertypes.");
    return new Outcome(
        "added "
            + supertype
            + " as a supertype of "
            + name
            + " ("
            + relation
            + ") in "
            + rel
            + written.fixed().said(),
        written.declared());
  }
}
