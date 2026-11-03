package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.ITypeBinding;

/** How the tools name a type in messages and stubs: its simple name with its enclosing types. */
final class Names {

  private Names() {}

  /** A type's name with its enclosing types: {@code Outer.Inner}. */
  static String typeLabel(ITypeBinding type) {
    ITypeBinding t = type.getErasure();
    return t.getDeclaringClass() == null
        ? t.getName()
        : typeLabel(t.getDeclaringClass()) + "." + t.getName();
  }
}
