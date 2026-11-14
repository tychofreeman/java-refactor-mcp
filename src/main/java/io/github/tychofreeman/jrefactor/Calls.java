package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/** What a call runs: one list of the nodes that call a method or a constructor. */
final class Calls {

  private Calls() {}

  /**
   * The method or constructor that {@code n} runs, when it is a call: a method call or a super
   * call, a construction or an enum constant, {@code this(...)} or {@code super(...)}, a method
   * reference. A construction of an anonymous class ({@code new T(...) {...}}, a constant with a
   * body) runs the superclass's constructor its arguments go to ({@link Signatures#constructed}).
   * Null for another node, or one that does not resolve.
   */
  static IMethodBinding binding(ASTNode n) {
    return switch (n) {
      case MethodInvocation m -> m.resolveMethodBinding();
      case SuperMethodInvocation m -> m.resolveMethodBinding();
      case ClassInstanceCreation c -> Signatures.constructed(c.resolveConstructorBinding());
      case EnumConstantDeclaration c -> Signatures.constructed(c.resolveConstructorBinding());
      case ConstructorInvocation c -> c.resolveConstructorBinding();
      case SuperConstructorInvocation c -> c.resolveConstructorBinding();
      case MethodReference r -> r.resolveMethodBinding();
      default -> null;
    };
  }
}
