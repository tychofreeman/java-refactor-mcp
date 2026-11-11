package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** What an expression's syntax and its locals' initializers say about whether it can be null. */
final class NonNull {

  private NonNull() {}

  /**
   * Whether {@code e} may be null: anything but this, a new object or array, a string or class
   * literal, one of those in parentheses or cast, or a local never reassigned whose initializer is
   * one of those.
   */
  static boolean mayBeNull(Expression e) {
    if (e instanceof ParenthesizedExpression p) {
      return mayBeNull(p.getExpression());
    }
    if (e instanceof CastExpression c) {
      return mayBeNull(c.getExpression());
    }
    if (e instanceof ThisExpression
        || e instanceof ClassInstanceCreation
        || e instanceof ArrayCreation
        || e instanceof StringLiteral
        || e instanceof TypeLiteral) {
      return false;
    }
    if (e instanceof SimpleName n
        && n.resolveBinding() instanceof IVariableBinding v
        && !v.isField()
        && !v.isParameter()
        && (v.isEffectivelyFinal() || Modifier.isFinal(v.getModifiers()))
        && n.getRoot() instanceof CompilationUnit unit
        && unit.findDeclaringNode(v) instanceof VariableDeclarationFragment f
        && f.getInitializer() != null) {
      return mayBeNull(f.getInitializer());
    }
    return true;
  }
}
