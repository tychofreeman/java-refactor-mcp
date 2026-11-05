package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * change_type: a field's declared type is replaced by a new one. The gate checks every use in the
 * project, so a change that a use no longer compiles with is refused and nothing is changed.
 */
final class ChangeType {

  private ChangeType() {}

  static Outcome run(SourceProject project, String rel, String name, String type) throws Exception {
    Precondition.require(
        Precondition.that(
            () -> type != null && !type.isBlank(),
            () -> "change_type needs type, the new type as written, such as long."));
    Qualified q = Qualified.of(name, "Type.field, the class and the field whose type to change");
    String typeName = q.type();
    String field = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    TypeDeclaration owner = opened.topLevel(typeName);
    FieldDeclaration decl = null;
    for (FieldDeclaration f : owner.getFields()) {
      for (Object o : f.fragments()) {
        if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(field)) {
          decl = f;
        }
      }
    }
    if (decl == null) {
      throw new Refusal(
          name
              + " is not a field of "
              + typeName
              + " in "
              + rel
              + ". For a method, pass parameter (whose type to change) or return_type=true.");
    }
    if (decl.fragments().size() != 1) {
      throw new Refusal(
          "the declaration of "
              + field
              + " declares several fields. Split it into one declaration per field first.");
    }
    Type old = decl.getType();
    int start = old.getStartPosition();
    int end = start + old.getLength();
    String oldText = text.substring(start, end);
    String updated = text.substring(0, start) + type + text.substring(end);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            updated,
            List.of(),
            "changing the type of " + name,
            "Change the uses that do not compile with the new type first, or keep the type.");
    return new Outcome(
        "changed the type of "
            + name
            + " from "
            + oldText
            + " to "
            + type
            + " in "
            + rel
            + written.fixed().said(),
        written.declared());
  }
}
