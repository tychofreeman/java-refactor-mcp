package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
    if (type == null || type.isBlank()) {
      throw new Refusal("change_type needs type, the new type as written, such as long.");
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      throw new Refusal("name is Type.field, the class and the field whose type to change.");
    }
    String typeName = name.substring(0, dot);
    String field = name.substring(dot + 1);
    Path file = project.javaFile(rel);
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    TypeDeclaration owner = MemberMove.typeNamed(unit, typeName);
    if (owner == null || owner.resolveBinding() == null) {
      throw new Refusal(typeName + " is not a top-level class of " + rel + " that resolves.");
    }
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
              + ". change_type changes a field's type; a method's signature is changed with"
              + " replace_declaration.");
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
