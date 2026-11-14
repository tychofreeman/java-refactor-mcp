package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/**
 * A Java file from its type's declaration: the package line, the type with an empty body, and a
 * stub for each abstract method its supertypes leave unimplemented.
 */
final class Skeleton {

  private Skeleton() {}

  /** The file's text, and the methods stubbed in it ("Priced.price()"). */
  record Made(String text, List<String> stubbed) {}

  /** The text of {@code path}, in package {@code pkg}, holding the type {@code declaration}. */
  static Made of(SourceProject project, Path path, String pkg, String declaration) {
    String header = declaration.strip();
    if (header.contains("{") || header.contains("}") || header.contains(";")) {
      throw new Refusal(
          "declaration is the header of a type (class Cart implements Priced, record Line(int"
              + " qty)), without a body; add members after with add_declaration.");
    }
    String start = pkg == null || pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
    String empty = start + header + " {}\n";
    CompilationUnit unit = Jdt.parse(project, path, empty);
    if (unit.types().size() != 1) {
      throw new Refusal(
          "declaration does not parse as one type: write it as a class, interface, enum or record"
              + " header, such as class Cart implements Priced.");
    }
    AbstractTypeDeclaration type = (AbstractTypeDeclaration) unit.types().get(0);
    String name = type.getName().getIdentifier();
    String file = path.getFileName().toString();
    if (!file.equals(name + ".java")) {
      throw new Refusal(
          "declaration declares "
              + name
              + ", but the file is "
              + file
              + ". Name one after the other.");
    }
    ITypeBinding binding = type.resolveBinding();
    List<IMethodBinding> missing = binding == null ? List.of() : unimplemented(binding);
    if (missing.isEmpty()) {
      return new Made(empty, List.of());
    }
    StringBuilder text = new StringBuilder(start).append(header).append(" {\n");
    List<String> stubbed = new ArrayList<>();
    for (int i = 0; i < missing.size(); i++) {
      IMethodBinding m = missing.get(i);
      text.append(i == 0 ? "" : "\n").append(stub(m));
      stubbed.add(m.getDeclaringClass().getErasure().getName() + "." + signature(m));
    }
    return new Made(text.append("}\n").toString(), stubbed);
  }

  /**
   * The abstract methods of {@code type}'s supertypes that nothing in its hierarchy implements, in
   * the order the supertypes declare them; none for an interface or an abstract class.
   */
  static List<IMethodBinding> unimplemented(ITypeBinding type) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      return List.of();
    }
    return leftAbstract(type, List.of());
  }

  private static void walk(
      ITypeBinding type, List<IMethodBinding> concrete, List<IMethodBinding> abstracts) {
    for (ITypeBinding t : Hierarchy.supertypes(type, true)) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        int mods = m.getModifiers();
        if (m.isConstructor() || Modifier.isStatic(mods) || Modifier.isPrivate(mods)) {
          continue;
        }
        boolean isAbstract =
            Modifier.isAbstract(mods) || (t.isInterface() && !Modifier.isDefault(mods));
        (isAbstract ? abstracts : concrete).add(m);
      }
    }
  }

  /** A "not implemented" method that implements {@code m}, indented one step of two spaces. */
  static String stub(IMethodBinding m) {
    int mods = m.getModifiers();
    String visibility =
        Modifier.isPublic(mods) || m.getDeclaringClass().isInterface()
            ? "public "
            : Modifier.isProtected(mods) ? "protected " : "";
    List<String> typeParameters = new ArrayList<>();
    for (ITypeBinding t : m.getTypeParameters()) {
      typeParameters.add(t.getName());
    }
    return "  @Override\n  "
        + visibility
        + (typeParameters.isEmpty() ? "" : "<" + String.join(", ", typeParameters) + "> ")
        + Extract.typeName(m.getReturnType())
        + " "
        + m.getName()
        + "("
        + String.join(", ", parameters(m))
        + ") {\n    throw new UnsupportedOperationException(\"not implemented\");\n  }\n";
  }

  /**
   * An override of the concrete method {@code m} that calls it ({@code super.m(a)}, or {@code
   * I.super.m(a)} for an interface's default method), so behavior is unchanged until it is edited;
   * indented one step of two spaces.
   */
  static String override(IMethodBinding m) {
    String stub = stub(m);
    List<String> names = new ArrayList<>();
    for (String p : parameters(m)) {
      names.add(p.substring(p.lastIndexOf(' ') + 1));
    }
    String receiver =
        m.getDeclaringClass().isInterface()
            ? m.getDeclaringClass().getErasure().getName() + ".super"
            : "super";
    String call = receiver + "." + m.getName() + "(" + String.join(", ", names) + ");";
    String body = m.getReturnType().getName().equals("void") ? call : "return " + call;
    return stub.replace("throw new UnsupportedOperationException(\"not implemented\");", body);
  }

  /** "Type name" for each parameter; a name a binary class lacks comes from its type. */
  private static List<String> parameters(IMethodBinding m) {
    ITypeBinding[] types = m.getParameterTypes();
    String[] names = m.getParameterNames();
    List<String> out = new ArrayList<>();
    Set<String> used = new HashSet<>();
    for (int i = 0; i < types.length; i++) {
      String name = i < names.length ? names[i] : "";
      if (name.isEmpty() || name.matches("arg\\d+")) {
        name = Names.parameterName(types[i]);
      }
      name = Names.free(name, used);
      used.add(name);
      out.add(Extract.typeName(types[i]) + " " + name);
    }
    return out;
  }

  /** "name(Type, Type)" with the parameter types as source names them. */
  private static String signature(IMethodBinding m) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : m.getParameterTypes()) {
      types.add(Extract.typeName(t));
    }
    return m.getName() + "(" + String.join(", ", types) + ")";
  }

  /**
   * The abstract methods of {@code type} and its supertypes that neither its hierarchy nor {@code
   * more} implements: what a class extending {@code type} and declaring {@code more} leaves
   * abstract.
   */
  static List<IMethodBinding> leftAbstract(ITypeBinding type, List<IMethodBinding> more) {
    List<IMethodBinding> concrete = new ArrayList<>(more);
    List<IMethodBinding> abstracts = new ArrayList<>();
    walk(type, concrete, abstracts);
    List<IMethodBinding> out = new ArrayList<>();
    for (IMethodBinding a : abstracts) {
      boolean done =
          concrete.stream().anyMatch(c -> c.isSubsignature(a))
              || out.stream().anyMatch(o -> o.isSubsignature(a));
      if (!done) {
        out.add(a);
      }
    }
    return out;
  }
}
