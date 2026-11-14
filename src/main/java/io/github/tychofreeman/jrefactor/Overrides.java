package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Whether an instance method a tool adds to a type would override a method its supertypes declare,
 * or be overridden by one a subtype in the project declares: either way a call would run another
 * body than before.
 */
final class Overrides {

  private Overrides() {}

  /**
   * "Square.area(int)": an instance method of {@code t} named {@code name} taking {@code params}.
   */
  private static String clash(ITypeBinding t, String name, List<String> params) {
    for (IMethodBinding m : t.getDeclaredMethods()) {
      if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers())) {
        continue;
      }
      List<String> theirs = new ArrayList<>();
      for (ITypeBinding p : m.getParameterTypes()) {
        theirs.add(p.getErasure().getQualifiedName());
      }
      if (theirs.equals(params)) {
        return Signatures.label(m);
      }
    }
    return null;
  }

  /**
   * A method that clashes with the new one, "Square.area(int)"; {@code subtype} is the type below
   * that declares it, or null when the new method would override it.
   */
  record Clash(String method, ITypeBinding subtype) {}

  /**
   * The first instance method named {@code name} taking {@code params} (erased qualified names) in
   * a supertype of {@code owner}, transitively, or else in a type of the project below it, found in
   * the files that name it ({@code file} is owner's); null when there is none.
   */
  static Clash of(
      SourceProject project, Path file, ITypeBinding owner, String name, List<String> params)
      throws IOException {
    for (ITypeBinding t : Hierarchy.supertypes(owner.getErasure(), false)) {
      String clash = clash(t, name, params);
      if (clash != null) {
        return new Clash(clash, null);
      }
    }
    String key = owner.getErasure().getKey();
    for (CompilationUnit u :
        Holding.of(project, owner.getErasure().getName(), file).units().values()) {
      for (TypeDeclaration t : Nodes.all(u, TypeDeclaration.class)) {
        ITypeBinding tb = t.resolveBinding();
        if (tb != null && !tb.getErasure().getKey().equals(key) && Hierarchy.isSubtype(tb, key)) {
          String clash = clash(tb, name, params);
          if (clash != null) {
            return new Clash(clash, tb);
          }
        }
      }
    }
    return null;
  }
}
