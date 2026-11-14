package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

/**
 * Keys that name what a binding declares, the same in every parse: compare bindings by these, not
 * by {@code getKey()}, which differs for a use through a parameterized type or a generic call.
 */
final class Bindings {
  private Bindings() {}

  /**
   * The key of what {@code b} declares, or null: a type's generic declaration, a method's or a
   * variable's declaration, so a use through a parameterized type ({@code box.value} of a {@code
   * Box<String>}) or a generic call ({@code id("x")}) has the declaration's key.
   */
  static String declarationKey(IBinding b) {
    return switch (b) {
      case null -> null;
      case ITypeBinding t -> t.getTypeDeclaration().getKey();
      case IMethodBinding m -> m.getMethodDeclaration().getKey();
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      default -> b.getKey();
    };
  }

  /** Whether {@code a} and {@code b}, from one parse or two, name the same declaration. */
  static boolean same(IBinding a, IBinding b) {
    String key = declarationKey(a);
    return key != null && key.equals(declarationKey(b));
  }
}
