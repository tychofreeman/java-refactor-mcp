package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

final class Implement {
  private Implement() {}

  /**
   * The members {@code what} asks of {@code type} (null: the file's only top-level type) in the
   * file {@code path}, whose text is {@code text}: for {@code *}, a "not implemented" stub of each
   * abstract method its supertypes leave; for a method's name ({@code describe}, {@code
   * Base.describe}, {@code describe(int)}), that method of a supertype, a stub when it is abstract,
   * else an override that calls the supertype's.
   */
  static String members(SourceProject project, Path path, String text, String type, String what)
      throws IOException {
    ITypeBinding t = typeIn(Jdt.parse(project, path, text), type);
    if (what.equals("*")) {
      List<IMethodBinding> left = Skeleton.unimplemented(t);
      if (left.isEmpty()) {
        throw new Refusal(
            t.getName()
                + " leaves no abstract method of its supertypes unimplemented; name a method to"
                + " override instead (implement=toString).");
      }
      List<String> out = new ArrayList<>();
      for (IMethodBinding m : left) {
        out.add(Skeleton.stub(m));
      }
      return String.join("\n", out);
    }
    String name = what;
    String owner = null;
    String params = null;
    int paren = name.indexOf('(');
    if (paren >= 0) {
      params =
          name.substring(paren + 1, Math.max(paren + 1, name.lastIndexOf(')'))).replace(" ", "");
      name = name.substring(0, paren);
    }
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      owner = name.substring(0, dot);
      name = name.substring(dot + 1);
    }
    List<IMethodBinding> found = new ArrayList<>();
    Set<String> labels = new TreeSet<>();
    for (IMethodBinding m : inherited(t)) {
      labels.add(label(m));
      if (m.getName().equals(name)
          && (owner == null || m.getDeclaringClass().getErasure().getName().equals(owner))
          && (params == null || types(m).equals(params))) {
        found.add(m);
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          "no supertype of "
              + t.getName()
              + " declares "
              + what
              + " for it to implement or override; it can take "
              + String.join(", ", labels)
              + ".");
    }
    if (found.size() > 1) {
      List<String> each = new ArrayList<>();
      for (IMethodBinding m : found) {
        each.add(label(m));
      }
      throw new Refusal(
          what + " is several methods: " + String.join(", ", each) + ". Name one of them.");
    }
    IMethodBinding m = found.get(0);
    return Modifier.isAbstract(m.getModifiers()) ? Skeleton.stub(m) : Skeleton.override(m);
  }

  /**
   * The methods {@code t} can implement or override, nearest first: those of its supertypes ({@code
   * Object}'s too) that are not private, static, final or constructors, and that neither it nor a
   * nearer supertype already declares.
   */
  private static List<IMethodBinding> inherited(ITypeBinding t) {
    List<IMethodBinding> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (IMethodBinding m : t.getDeclaredMethods()) {
      seen.add(m.getName() + "(" + types(m) + ")");
    }
    for (ITypeBinding s : Hierarchy.supertypes(t, false)) {
      for (IMethodBinding m : s.getDeclaredMethods()) {
        int mods = m.getModifiers();
        if (m.isConstructor()
            || Modifier.isPrivate(mods)
            || Modifier.isStatic(mods)
            || Modifier.isFinal(mods)
            || !seen.add(m.getName() + "(" + types(m) + ")")) {
          continue;
        }
        out.add(m);
      }
    }
    return out;
  }

  /** The type named {@code type} in {@code unit} (Outer.Inner), or its only top-level type. */
  private static ITypeBinding typeIn(CompilationUnit unit, String type) {
    for (AbstractTypeDeclaration d : Nodes.all(unit, AbstractTypeDeclaration.class)) {
      ITypeBinding b = d.resolveBinding();
      if (b != null
          && (type == null
              ? d.getParent() == unit && unit.types().size() == 1
              : Names.typeLabel(b).equals(type) || b.getName().equals(type))) {
        return b;
      }
    }
    throw new Refusal(
        type == null
            ? "the file has several top-level types; name one with type."
            : "no type " + type + " in the file that resolves.");
  }

  /** "Base.describe(int)" for a refusal. */
  private static String label(IMethodBinding m) {
    return m.getDeclaringClass().getErasure().getName() + "." + m.getName() + "(" + types(m) + ")";
  }

  /** The parameter types of {@code m} as source names them, joined by commas, no spaces. */
  private static String types(IMethodBinding m) {
    List<String> out = new ArrayList<>();
    for (ITypeBinding p : m.getParameterTypes()) {
      out.add(Extract.typeName(p).replace(" ", ""));
    }
    return String.join(",", out);
  }
}
