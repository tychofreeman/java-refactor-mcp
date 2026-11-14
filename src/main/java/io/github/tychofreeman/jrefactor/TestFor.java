package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;

final class TestFor {
  private TestFor() {}

  /** A test framework: its annotation, its fail method's class, and whether tests are public. */
  record Framework(String test, String assertions, boolean isPublic) {}

  /** What a test is for: a type, and one of its methods or null. */
  record Target(ITypeBinding type, IMethodBinding method) {

    /** "Cart.add(int)", or "Cart". */
    String label() {
      return method == null ? type.getName() : type.getName() + "." + Names.signature(method);
    }
  }

  /** JUnit Jupiter when the classpath has it, else JUnit 4; refused with neither. */
  static Framework framework(SourceProject project) throws IOException {
    if (Imports.known(project, "org.junit.jupiter.api.Test")) {
      return new Framework("org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions", false);
    }
    if (Imports.known(project, "org.junit.Test")) {
      return new Framework("org.junit.Test", "org.junit.Assert", true);
    }
    throw new Refusal(
        "test_for needs JUnit (Jupiter or 4) on the project's classpath; add it to the build, or"
            + " write the test with content.");
  }

  /**
   * The target {@code name} names: "Cart", "Cart.total", or "Cart.add(int)" for one of several
   * overloads; found anywhere in the project.
   */
  static Target target(SourceProject project, String name) throws IOException {
    int paren = name.indexOf('(');
    String path = paren < 0 ? name : name.substring(0, paren);
    String params =
        paren < 0 ? null : name.substring(paren + 1, name.lastIndexOf(')')).replace(" ", "");
    int dot = path.lastIndexOf('.');
    String type =
        dot > 0 && paren >= 0 || dot > 0 && Character.isLowerCase(path.charAt(dot + 1))
            ? path.substring(0, dot)
            : path;
    String member = type.equals(path) ? null : path.substring(dot + 1);
    String simple = Names.simpleName(type);
    Holding holding = Holding.of(project, simple);
    for (CompilationUnit unit : holding.units().values()) {
      for (AbstractTypeDeclaration t : Nodes.all(unit, AbstractTypeDeclaration.class)) {
        ITypeBinding b = t.resolveBinding();
        String chain = b == null ? "" : b.getQualifiedName();
        if (b == null || !(chain.equals(type) || chain.endsWith("." + type))) {
          continue;
        }
        if (member == null) {
          return new Target(b, null);
        }
        List<IMethodBinding> found = new ArrayList<>();
        for (IMethodBinding m : b.getDeclaredMethods()) {
          if (m.getName().equals(member)
              && !m.isConstructor()
              && (params == null || params.equals(String.join(",", types(m)).replace(" ", "")))) {
            found.add(m);
          }
        }
        if (found.isEmpty()) {
          throw new Refusal(
              type + " has no method " + member + (params == null ? "" : "(" + params + ")") + ".");
        }
        if (found.size() > 1) {
          List<String> each = new ArrayList<>();
          for (IMethodBinding m : found) {
            each.add(b.getName() + "." + Names.signature(m));
          }
          throw new Refusal(
              type
                  + "."
                  + member
                  + " has "
                  + found.size()
                  + " overloads; name one: "
                  + String.join(", ", each)
                  + ".");
        }
        return new Target(b, found.get(0));
      }
    }
    throw new Refusal(
        "no type "
            + type
            + " in the project. Name the target as Type, Type.method, or"
            + " Type.method(int) for one overload.");
  }

  /**
   * A red test method for {@code target}'s method, named {@code name}: the receiver from its
   * fewest-argument constructor, the call, and {@code fail} (written {@code fail} when {@code
   * staticFail}, else through the assertions class), at an indent of two spaces.
   */
  static String method(Target target, Framework f, String name, boolean staticFail) {
    ITypeBinding type = target.type();
    IMethodBinding m = target.method();
    String typeName = Extract.typeName(type.getErasure());
    String receiver = decapitalize(type.getName());
    StringBuilder body = new StringBuilder();
    boolean isStatic = Modifier.isStatic(m.getModifiers());
    if (!isStatic) {
      body.append("    ")
          .append(typeName)
          .append(" ")
          .append(receiver)
          .append(" = new ")
          .append(typeName)
          .append("(")
          .append(placeholders(constructor(type)))
          .append(");\n");
    }
    String call =
        (isStatic ? typeName : receiver) + "." + m.getName() + "(" + placeholders(m) + ")";
    ITypeBinding result = m.getReturnType();
    if (result.getName().equals("void")) {
      body.append("    ").append(call).append(";\n");
    } else {
      String local = m.getName().equals(receiver) ? "result" : m.getName();
      String resultType = result.isTypeVariable() ? "Object" : Extract.typeName(result);
      body.append("    ")
          .append(resultType)
          .append(" ")
          .append(local)
          .append(" = ")
          .append(call)
          .append(";\n");
    }
    String fail = staticFail ? "fail" : Names.simpleName(f.assertions()) + ".fail";
    body.append("    ").append(fail).append("(\"write the assertion\");\n");
    return "  @Test\n  "
        + (f.isPublic() ? "public " : "")
        + "void "
        + name
        + "() {\n"
        + body
        + "  }\n";
  }

  /**
   * A test file's text: package {@code pkg}, the framework's imports, and class {@code className}
   * with a red test of {@code target}'s method, or empty for a type.
   */
  static String file(String pkg, String className, Target target, Framework f) {
    StringBuilder text = new StringBuilder();
    if (pkg != null && !pkg.isEmpty()) {
      text.append("package ").append(pkg).append(";\n\n");
    }
    if (target.method() == null) {
      return text.append(f.isPublic() ? "public " : "")
          .append("class ")
          .append(className)
          .append(" {}\n")
          .toString();
    }
    text.append("import static ").append(f.assertions()).append(".fail;\n\n");
    text.append("import ").append(f.test()).append(";\n\n");
    text.append(f.isPublic() ? "public " : "").append("class ").append(className).append(" {\n");
    text.append(method(target, f, target.method().getName(), true));
    return text.append("}\n").toString();
  }

  /** What a result says of a red test written for {@code target}. */
  static String note(Target target) {
    return "; a red test of " + target.label() + ": write its assertion in place of fail";
  }

  /** {@code name}, or {@code name2}, {@code name3}, ... when a method of {@code text} has it. */
  static String freeName(String text, String name) {
    Set<String> taken = new HashSet<>();
    for (MethodDeclaration m : Nodes.all(Jdt.parseSyntax(text), MethodDeclaration.class)) {
      taken.add(m.getName().getIdentifier());
    }
    String out = name;
    for (int n = 2; taken.contains(out); n++) {
      out = name + n;
    }
    return out;
  }

  /** The non-private constructor with the fewest parameters; null when the type declares none. */
  private static IMethodBinding constructor(ITypeBinding type) {
    IMethodBinding best = null;
    boolean any = false;
    for (IMethodBinding m : type.getDeclaredMethods()) {
      if (!m.isConstructor()) {
        continue;
      }
      any = true;
      if (!Modifier.isPrivate(m.getModifiers())
          && (best == null || m.getParameterTypes().length < best.getParameterTypes().length)) {
        best = m;
      }
    }
    if (any && best == null) {
      throw new Refusal(
          type.getName()
              + " has only private constructors, so a test cannot build one; test a static"
              + " method of it, or write the test with content.");
    }
    return best;
  }

  /** Placeholder arguments for {@code m}'s parameters: 0, false, '0', "", or null. */
  private static String placeholders(IMethodBinding m) {
    if (m == null) {
      return "";
    }
    List<String> out = new ArrayList<>();
    for (ITypeBinding t : m.getParameterTypes()) {
      out.add(JavaTypes.placeholder(t.getName()));
    }
    return String.join(", ", out);
  }

  private static List<String> types(IMethodBinding m) {
    List<String> out = new ArrayList<>();
    for (ITypeBinding t : m.getParameterTypes()) {
      out.add(Extract.typeName(t));
    }
    return out;
  }

  private static String decapitalize(String name) {
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
