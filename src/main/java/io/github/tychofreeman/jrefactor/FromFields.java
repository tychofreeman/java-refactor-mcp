package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
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

  /**
   * {@code fixed}, the edit of {@code rel} from {@code before}, with a constructor for each class
   * whose final instance fields have no value, when the edit added one of them and no constructor
   * but a "not implemented" stub sets them: it takes the fields in order and goes after the last,
   * replacing a stub of the same parameter types. The notes name each, and say when a top-level
   * class then fits a record.
   */
  static Imports.Fixed complete(String before, Imports.Fixed fixed, String rel) {
    Set<String> had = new HashSet<>();
    if (!before.isBlank()) {
      blankFinals(Edits.parses(before, rel))
          .forEach((type, fields) -> fields.forEach(f -> had.add(type + "." + f)));
    }
    String text = fixed.text();
    List<String> notes = new ArrayList<>(fixed.notes());
    Set<String> done = new HashSet<>();
    while (true) {
      String type = null;
      List<String> fields = null;
      for (Map.Entry<String, List<String>> e : blankFinals(Edits.parses(text, rel)).entrySet()) {
        if (!done.contains(e.getKey())
            && e.getValue().stream().anyMatch(f -> !had.contains(e.getKey() + "." + f))) {
          type = e.getKey();
          fields = e.getValue();
          break;
        }
      }
      if (type == null) {
        return new Imports.Fixed(text, fixed.lineShift(), notes);
      }
      done.add(type);
      try {
        String content = constructor(text, rel, type, fields);
        text = Declarations.plan(text, rel, type, content).text();
        String simple = type.substring(type.lastIndexOf('.') + 1);
        notes.add(
            "added constructor "
                + content.substring(content.indexOf(simple + "("), content.indexOf(')') + 1)
                + ", since final fields "
                + String.join(", ", fields)
                + " were unset"
                + (fitsRecord(Edits.parses(text, rel), type)
                    ? "; " + simple + " now fits a record: convert_to_record makes it one"
                    : ""));
      } catch (Refusal r) {
        // The gate then refuses the unset fields, naming constructor.
      }
    }
  }

  /**
   * The classes of {@code unit} with final instance fields that have no value, by Outer.Inner, with
   * those fields in order, when no initializer or constructor but a stub could set them.
   */
  private static Map<String, List<String>> blankFinals(CompilationUnit unit) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (TypeDeclaration t : Nodes.all(unit, TypeDeclaration.class)) {
      String path = path(t);
      if (t.isInterface() || path == null) {
        continue;
      }
      List<String> fields = new ArrayList<>();
      boolean set = false;
      for (Object o : t.bodyDeclarations()) {
        if (o instanceof Initializer i && !Modifier.isStatic(i.getModifiers())) {
          set = true;
        } else if (o instanceof MethodDeclaration m
            && m.isConstructor()
            && !Declarations.isStub(m)) {
          set = true;
        } else if (o instanceof FieldDeclaration f
            && Modifier.isFinal(f.getModifiers())
            && !Modifier.isStatic(f.getModifiers())) {
          for (Object v : f.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) v;
            if (fragment.getInitializer() == null) {
              fields.add(fragment.getName().getIdentifier());
            }
          }
        }
      }
      if (!set && !fields.isEmpty()) {
        out.put(path, fields);
      }
    }
    return out;
  }

  /** Outer.Inner for a type declared in the unit or in another type; null for a local class. */
  private static String path(AbstractTypeDeclaration t) {
    String name = t.getName().getIdentifier();
    if (t.getParent() instanceof CompilationUnit) {
      return name;
    }
    if (t.getParent() instanceof AbstractTypeDeclaration outer) {
      String p = path(outer);
      return p == null ? null : p + "." + name;
    }
    return null;
  }

  /**
   * Whether the top-level class {@code type} is what convert_to_record takes: no superclass, not
   * abstract, its instance fields final, and one constructor.
   */
  private static boolean fitsRecord(CompilationUnit unit, String type) {
    for (Object o : unit.types()) {
      if (o instanceof TypeDeclaration t && t.getName().getIdentifier().equals(type)) {
        if (t.isInterface()
            || t.getSuperclassType() != null
            || Modifier.isAbstract(t.getModifiers())) {
          return false;
        }
        int constructors = 0;
        for (MethodDeclaration m : t.getMethods()) {
          constructors += m.isConstructor() ? 1 : 0;
        }
        for (FieldDeclaration f : t.getFields()) {
          if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
            return false;
          }
        }
        return constructors == 1;
      }
    }
    return false;
  }
}
