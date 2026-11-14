package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;

/**
 * The uses at which a change between primitive or boxed types changes what code does: an int that
 * becomes a long no longer overflows in {@code x + 1}, one that becomes a double divides as a real
 * in {@code x / 2}, a char that becomes an int prints as a number in {@code println(c)}, and two
 * boxed values compare by identity with {@code ==}. change_type of or to such a type refuses a use
 * where the type decides the result; a plain copy (an assignment, an initializer, a return) and an
 * ordering comparison keep it, and a narrowing copy is a compile error the gate refuses.
 */
final class TypeDecidedUses {

  private TypeDecidedUses() {}

  /** Whether a change from {@code old} to the type written {@code type} needs the check. */
  static boolean applies(ITypeBinding old, String type) {
    return old != null && (old.isPrimitive() || boxed(old) || named(type));
  }

  /** Whether {@code old} or the type written {@code type} is a boxed type. */
  static boolean anyBoxed(ITypeBinding old, String type) {
    String simple = type.strip().substring(type.strip().lastIndexOf('.') + 1);
    return (old != null && boxed(old)) || JavaTypes.isBox(simple);
  }

  /**
   * Refuses when {@code use}, an expression whose value is what changes type ({@code what}), is
   * used where the type decides the result.
   */
  static void check(
      Expression use, String what, String from, String to, boolean boxed, String rel) {
    String why = risky(use, boxed);
    if (why != null) {
      CompilationUnit unit = (CompilationUnit) use.getRoot();
      throw new Refusal(
          what
              + " is "
              + why
              + " at line "
              + unit.getLineNumber(use.getStartPosition())
              + " of "
              + rel
              + ", where changing it from "
              + from
              + " to "
              + to
              + " could change the result (an overflow, a division, the overload chosen, what is"
              + " printed). Change that use first, or keep the type.");
    }
  }

  /** The expression a name's value is: {@code x.f} or {@code this.f} for the {@code f} of it. */
  static Expression value(SimpleName n) {
    ASTNode p = n.getParent();
    if ((p instanceof QualifiedName q && q.getName() == n)
        || (p instanceof FieldAccess f && f.getName() == n)) {
      return (Expression) p;
    }
    return n;
  }

  /** Where {@code use} is put that its type decides, in words, or null for a copy or comparison. */
  private static String risky(Expression use, boolean boxed) {
    ASTNode p = use.getParent();
    while (p instanceof ParenthesizedExpression paren) {
      use = paren;
      p = paren.getParent();
    }
    Expression e = use;
    return switch (p) {
      case InfixExpression in -> infix(in.getOperator(), boxed);
      case PrefixExpression pre when pre.getOperator() != PrefixExpression.Operator.NOT ->
          "an operand of " + pre.getOperator();
      case PostfixExpression post -> "an operand of " + post.getOperator();
      case Assignment a
          when a.getLeftHandSide() == e && a.getOperator() != Assignment.Operator.ASSIGN ->
          "the target of " + a.getOperator();
      case MethodInvocation mi when mi.arguments().contains(e) -> "an argument of " + mi.getName();
      case MethodInvocation mi when boxed && mi.getExpression() == e ->
          "the receiver of " + mi.getName();
      case SuperMethodInvocation mi when mi.arguments().contains(e) ->
          "an argument of " + mi.getName();
      case ClassInstanceCreation c when c.arguments().contains(e) ->
          "an argument of new " + c.getType();
      case ConstructorInvocation c when c.arguments().contains(e) -> "an argument of this(...)";
      case SuperConstructorInvocation c when c.arguments().contains(e) ->
          "an argument of super(...)";
      case CastExpression c -> "cast to " + c.getType();
      case ConditionalExpression c when c.getExpression() != e -> "a branch of ?:";
      case SwitchStatement s -> "a switch selector";
      case SwitchExpression s -> "a switch selector";
      default -> null;
    };
  }

  private static String infix(InfixExpression.Operator op, boolean boxed) {
    if (op == InfixExpression.Operator.LESS
        || op == InfixExpression.Operator.GREATER
        || op == InfixExpression.Operator.LESS_EQUALS
        || op == InfixExpression.Operator.GREATER_EQUALS
        || op == InfixExpression.Operator.CONDITIONAL_AND
        || op == InfixExpression.Operator.CONDITIONAL_OR) {
      return null;
    }
    if (op == InfixExpression.Operator.EQUALS || op == InfixExpression.Operator.NOT_EQUALS) {
      return boxed ? "compared with " + op + ", by identity for a boxed type" : null;
    }
    return "an operand of " + op;
  }

  private static boolean boxed(ITypeBinding t) {
    return t.getPackage() != null
        && t.getPackage().getName().equals("java.lang")
        && JavaTypes.isBox(t.getName());
  }

  private static boolean named(String type) {
    String simple = type.strip().substring(type.strip().lastIndexOf('.') + 1);
    return JavaTypes.isPrimitive(simple) || JavaTypes.isBox(simple);
  }
}
