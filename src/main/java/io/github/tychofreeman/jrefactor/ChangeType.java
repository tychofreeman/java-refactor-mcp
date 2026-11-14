package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
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
    Signatures.parsesAs("type", type, "class X { " + type + " x; }");
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
    if (!((VariableDeclarationFragment) decl.fragments().get(0)).extraDimensions().isEmpty()) {
      throw new Refusal(name + " writes its [] after its name. Write it after the type first.");
    }
    checkUses(project, file, decl, field, type);
    CallTargets targets = CallTargets.of(Holding.of(project, field, file).units().values());
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
            wildcardFix(
                type,
                oldText,
                field,
                "Change the uses that do not compile with the new type first, or keep the type."));
    targets.require(project, "changing the type of " + name + " to " + type);
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

  /**
   * Refuses a change of or to a primitive or boxed type when a use of the field in the project is
   * one whose result the type decides ({@link TypeDecidedUses}).
   */
  private static void checkUses(
      SourceProject project, Path file, FieldDeclaration decl, String field, String type)
      throws IOException {
    ITypeBinding before = decl.getType().resolveBinding();
    IVariableBinding binding =
        ((VariableDeclarationFragment) decl.fragments().get(0)).resolveBinding();
    if (binding == null || !TypeDecidedUses.applies(before, type)) {
      return;
    }
    boolean boxed = TypeDecidedUses.anyBoxed(before, type);
    String key = binding.getVariableDeclaration().getKey();
    Holding holding = Holding.of(project, field, file);
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (!n.isDeclaration()
            && n.resolveBinding() instanceof IVariableBinding v
            && v.getVariableDeclaration().getKey().equals(key)) {
          TypeDecidedUses.check(
              TypeDecidedUses.value(n),
              field,
              before.getName(),
              type,
              boxed,
              project.relative(e.getKey()).toString());
        }
      }
    }
  }

  /**
   * The gate's fix line for a change from {@code old} to {@code type}, the type of {@code name}:
   * {@code fix}, and before it, when {@code type} holds a wildcard, what the wildcard does not
   * allow and which one to try.
   */
  static String wildcardFix(String type, String old, String name, String fix) {
    if (type.matches(".*\\?\\s*super\\b.*")) {
      return "What is read from "
          + type
          + " is an Object, and it cannot be passed on as "
          + old
          + ". If the code only reads "
          + name
          + ", try "
          + type.replaceAll("\\?\\s*super\\b", "? extends")
          + "; if it reads and adds, keep "
          + old
          + ".\n"
          + fix;
    }
    if (type.matches(".*\\?\\s*extends\\b.*")) {
      return type
          + " can be read from but not added to, and cannot be passed on as "
          + old
          + ". If the code only adds to "
          + name
          + ", try "
          + type.replaceAll("\\?\\s*extends\\b", "? super")
          + "; if it reads and adds, keep "
          + old
          + ".\n"
          + fix;
    }
    if (type.contains("?")) {
      return type
          + " can be read from but not added to, and cannot be passed on as "
          + old
          + ". If the code adds to "
          + name
          + ", keep "
          + old
          + ".\n"
          + fix;
    }
    return fix;
  }
}
