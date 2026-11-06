package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

final class FromFields {
  private FromFields() {}

  /**
   * A constructor of {@code type} (null: the file's only top-level type) in {@code text}, the file
   * {@code rel}, that takes {@code fields} in order, each a parameter of the field's type as
   * written, and assigns it; with the class's access.
   */
  static String constructor(String text, String rel, String type, List<String> fields) {
    Declarations.Owner owner = Declarations.owner(Edits.parses(text, rel), type, rel);
    String name = owner.decl().getName().getIdentifier();
    if (!(owner.decl() instanceof TypeDeclaration t) || t.isInterface()) {
      throw new Refusal(
          name
              + " is not a class; constructor writes a class's constructor (a record has its"
              + " canonical one).");
    }
    if (fields.isEmpty()) {
      throw new Refusal("constructor names the fields the constructor takes, in order.");
    }
    Map<String, String> types = new LinkedHashMap<>();
    Map<String, String> why = new LinkedHashMap<>();
    for (FieldDeclaration f : t.getFields()) {
      for (Object o : f.fragments()) {
        VariableDeclarationFragment v = (VariableDeclarationFragment) o;
        String field = v.getName().getIdentifier();
        types.put(field, f.getType() + "[]".repeat(v.getExtraDimensions()));
        if (Modifier.isStatic(f.getModifiers())) {
          why.put(field, field + " is static");
        } else if (Modifier.isFinal(f.getModifiers()) && v.getInitializer() != null) {
          why.put(field, field + " is final and has a value");
        }
      }
    }
    List<String> params = new ArrayList<>();
    List<String> body = new ArrayList<>();
    for (String field : fields) {
      if (!types.containsKey(field)) {
        throw new Refusal(
            field
                + " is not a field of "
                + name
                + "; its fields are "
                + String.join(", ", types.keySet())
                + ".");
      }
      if (why.containsKey(field)) {
        throw new Refusal(why.get(field) + ", so a constructor cannot set it. Leave it out.");
      }
      params.add(types.get(field) + " " + field);
      body.add("  this." + field + " = " + field + ";");
    }
    int mods = t.getModifiers();
    String access =
        Modifier.isPublic(mods)
            ? "public "
            : Modifier.isProtected(mods)
                ? "protected "
                : Modifier.isPrivate(mods) ? "private " : "";
    return access
        + name
        + "("
        + String.join(", ", params)
        + ") {\n"
        + String.join("\n", body)
        + "\n}\n";
  }
}
