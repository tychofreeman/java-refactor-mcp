package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/**
 * A type's supertypes and what its methods override: the one walk above a type for the tools that
 * look there.
 */
final class Hierarchy {

  private static final String OBJECT_KEY = "Ljava/lang/Object;";

  private Hierarchy() {}

  /**
   * The supertypes of {@code t}, transitively, breadth-first so the nearest come first, each once
   * by its erasure's key (an interface two paths reach, too); {@code t} itself first when {@code
   * self}. None for null.
   */
  static List<ITypeBinding> supertypes(ITypeBinding t, boolean self) {
    List<ITypeBinding> out = new ArrayList<>();
    if (t == null) {
      return out;
    }
    List<ITypeBinding> todo = new ArrayList<>(List.of(t));
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding each = todo.get(i);
      if (!seen.add(each.getErasure().getKey())) {
        continue;
      }
      if (i > 0 || self) {
        out.add(each);
      }
      if (each.getSuperclass() != null) {
        todo.add(each.getSuperclass());
      }
      todo.addAll(List.of(each.getInterfaces()));
    }
    return out;
  }

  /** The methods of {@code m}'s class's supertypes that {@code m} overrides, the nearest first. */
  static List<IMethodBinding> overridden(IMethodBinding m) {
    return overridden(m, m.getDeclaringClass());
  }

  /**
   * Whether {@code t} is {@code of} or a subtype of it, by erasure key, so the two may come from
   * two parses. Every reference type is an {@code Object}, an interface too.
   */
  static boolean isSubtype(ITypeBinding t, ITypeBinding of) {
    if (t == null || of == null || t.isPrimitive() || of.isPrimitive()) {
      return false;
    }
    return isSubtype(t, of.getErasure().getKey());
  }

  /**
   * Whether {@code t} is the type named {@code qualified} or a subtype of it, by erased qualified
   * name: for a library type the code knows by name. Every reference type is an {@code Object}.
   */
  static boolean isA(ITypeBinding t, String qualified) {
    if (t == null || t.isPrimitive()) {
      return false;
    }
    if (qualified.equals("java.lang.Object")) {
      return true;
    }
    for (ITypeBinding s : supertypes(t, true)) {
      if (s.getErasure().getQualifiedName().equals(qualified)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The methods of {@code from}'s supertypes (not {@code from}'s own) that {@code m} overrides, the
   * nearest first.
   */
  static List<IMethodBinding> overridden(IMethodBinding m, ITypeBinding from) {
    List<IMethodBinding> out = new ArrayList<>();
    if (m.isConstructor()) {
      return out;
    }
    for (ITypeBinding t : supertypes(from, false)) {
      for (IMethodBinding other : t.getDeclaredMethods()) {
        if (m.overrides(other)) {
          out.add(other);
        }
      }
    }
    return out;
  }

  /** As {@link #isSubtype(ITypeBinding, ITypeBinding)}, the supertype by its erasure's key. */
  static boolean isSubtype(ITypeBinding t, String key) {
    if (t == null || t.isPrimitive()) {
      return false;
    }
    if (key.equals(OBJECT_KEY)) {
      return true;
    }
    for (ITypeBinding s : supertypes(t, true)) {
      if (s.getErasure().getKey().equals(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The first concrete method of {@code type}'s supertypes (not {@code type}'s own) that {@code
   * method} overrides, the nearest, or null.
   */
  static IMethodBinding concreteOverridden(IMethodBinding method, ITypeBinding type) {
    for (IMethodBinding m : overridden(method, type)) {
      if (!Modifier.isAbstract(m.getModifiers())) {
        return m;
      }
    }
    return null;
  }
}
