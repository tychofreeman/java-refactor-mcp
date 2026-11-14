package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

/**
 * The nested type {@code move} takes out of its outer type: its range in the outer file, its
 * package, its name as given (Outer.Inner) and simple, its binding key and its outer type's key and
 * label.
 */
record MovedType(
    int start,
    int end,
    String pkg,
    String name,
    String simple,
    String key,
    String outerKey,
    String outerLabel) {

  /** Whether {@code offset} of the outer file is inside the moved type. */
  boolean inside(int offset) {
    return offset >= start && offset < end;
  }

  /** "Shop.label(String)", "Line.price": a widened member, its type named as after the move. */
  String label(IBinding d) {
    ITypeBinding owner =
        d instanceof IMethodBinding m
            ? m.getDeclaringClass()
            : d instanceof IVariableBinding v
                ? v.getDeclaringClass()
                : ((ITypeBinding) d).getDeclaringClass();
    String in =
        MoveType.declaredBy(owner, key)
            ? simple
            : MoveType.declaredBy(owner, outerKey) ? outerLabel : owner.getName();
    if (d instanceof IMethodBinding m) {
      List<String> types = new ArrayList<>();
      for (ITypeBinding t : m.getParameterTypes()) {
        types.add(t.getErasure().getName());
      }
      String params = "(" + String.join(", ", types) + ")";
      return m.isConstructor() ? in + params : in + "." + m.getName() + params;
    }
    return in + "." + d.getName();
  }

  /**
   * {@code b}'s name from its package once this type is top-level: "Outer.Other", "Inner",
   * "Inner.Nested".
   */
  String movedName(ITypeBinding b) {
    String q = b.getQualifiedName();
    String rel = pkg.isEmpty() ? q : q.substring(pkg.length() + 1);
    return rel.equals(name) || rel.startsWith(name + ".")
        ? simple + rel.substring(name.length())
        : rel;
  }
}
