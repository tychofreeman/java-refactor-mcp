package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * Which guards keep code that may throw from throwing (docs/design.md, Decisions, 2026-11-13): for
 * an integer division or remainder and a dereference, only a guard that tests the divisor or the
 * receiver; for anything else that may throw, any guard.
 */
final class Guards {

  /** Why a guard that is left behind does not keep the code from throwing, in a report. */
  static final String UNTESTED = "does not test what it divides by or dereferences";

  private Guards() {}

  /**
   * What an exception depends on: a divisor ({@code divisor}), else a receiver that may be null.
   */
  record Operand(Expression expression, boolean divisor) {}

  /**
   * What each of {@code throwing} fails on: the divisors of an integer {@code /} or {@code %} and
   * the receivers of a dereference; null when one may throw otherwise (an array element, a cast, a
   * call, a new object), which any guard keeps.
   */
  static List<Operand> operands(List<ASTNode> throwing) {
    List<Operand> out = new ArrayList<>();
    for (ASTNode n : throwing) {
      if (n instanceof InfixExpression in) {
        out.add(new Operand(in.getRightOperand(), true));
        for (Object x : in.extendedOperands()) {
          out.add(new Operand((Expression) x, true));
        }
      } else if (n instanceof QualifiedName q) {
        out.add(new Operand(q.getQualifier(), false));
      } else if (n instanceof FieldAccess f) {
        out.add(new Operand(f.getExpression(), false));
      } else if (n instanceof MethodInvocation m
          && m.getExpression() != null
          && m.resolveMethodBinding() instanceof IMethodBinding b
          && b.isSyntheticRecordMethod()) {
        out.add(new Operand(m.getExpression(), false));
      } else {
        return null;
      }
    }
    return out;
  }

  /**
   * The conditions of the guards {@code code} leaves when it moves to {@code landing}: those of the
   * ifs, {@code ?:}, {@code &&} and {@code ||}, loops and switches around it below {@code root}
   * whose part that holds it does not hold {@code landing} too; null when it leaves a catch clause,
   * a lambda or an anonymous class, which run it at another time rather than guard it.
   */
  static List<Expression> left(ASTNode code, ASTNode root, ASTNode landing) {
    List<Expression> out = new ArrayList<>();
    for (ASTNode n = code, p = code.getParent(); p != null && n != root; n = p, p = p.getParent()) {
      if (p instanceof CatchClause
          || p instanceof LambdaExpression
          || p instanceof AnonymousClassDeclaration) {
        return null;
      }
      if (Nodes.within(landing, n)) {
        continue;
      }
      if (p instanceof IfStatement i && n != i.getExpression()) {
        out.add(i.getExpression());
      } else if (p instanceof ConditionalExpression c && n != c.getExpression()) {
        out.add(c.getExpression());
      } else if (p instanceof InfixExpression in
          && (in.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
              || in.getOperator() == InfixExpression.Operator.CONDITIONAL_OR)) {
        out.add(in);
      } else if (p instanceof WhileStatement w) {
        out.add(w.getExpression());
      } else if (p instanceof DoStatement d) {
        out.add(d.getExpression());
      } else if (p instanceof ForStatement f && f.getExpression() != null) {
        out.add(f.getExpression());
      } else if (p instanceof EnhancedForStatement ef) {
        out.add(ef.getExpression());
      } else if (p instanceof SwitchStatement s) {
        out.add(s.getExpression());
      } else if (p instanceof SwitchExpression x) {
        out.add(x.getExpression());
      }
    }
    return out;
  }

  /**
   * Whether any of {@code guards} tests one of {@code operands}: compares a divisor to a constant
   * number ({@code d == 0}, {@code d > 0}), or a receiver to null ({@code x != null}, {@code
   * Objects.isNull(x)}, {@code Objects.nonNull(x)}, {@code x instanceof T}), or switches on it.
   */
  static boolean tests(List<? extends ASTNode> guards, List<Operand> operands) {
    for (ASTNode guard : guards) {
      for (Operand o : operands) {
        if (guard instanceof Expression e && same(e, o.expression())) {
          return true;
        }
        for (InfixExpression in : Nodes.all(guard, InfixExpression.class)) {
          if (compares(in) && !in.hasExtendedOperands()) {
            Expression l = in.getLeftOperand();
            Expression r = in.getRightOperand();
            if ((same(l, o.expression()) && against(r, o.divisor()))
                || (same(r, o.expression()) && against(l, o.divisor()))) {
              return true;
            }
          }
        }
        if (!o.divisor()) {
          for (InstanceofExpression in : Nodes.all(guard, InstanceofExpression.class)) {
            if (same(in.getLeftOperand(), o.expression())) {
              return true;
            }
          }
          for (PatternInstanceofExpression in :
              Nodes.all(guard, PatternInstanceofExpression.class)) {
            if (same(in.getLeftOperand(), o.expression())) {
              return true;
            }
          }
          for (MethodInvocation m : Nodes.all(guard, MethodInvocation.class)) {
            if ((m.getName().getIdentifier().equals("isNull")
                    || m.getName().getIdentifier().equals("nonNull"))
                && m.arguments().size() == 1
                && m.resolveMethodBinding() instanceof IMethodBinding b
                && b.getDeclaringClass().getQualifiedName().equals("java.util.Objects")
                && same((Expression) m.arguments().get(0), o.expression())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean compares(InfixExpression in) {
    InfixExpression.Operator op = in.getOperator();
    return op == InfixExpression.Operator.EQUALS
        || op == InfixExpression.Operator.NOT_EQUALS
        || op == InfixExpression.Operator.LESS
        || op == InfixExpression.Operator.LESS_EQUALS
        || op == InfixExpression.Operator.GREATER
        || op == InfixExpression.Operator.GREATER_EQUALS;
  }

  /** Whether {@code e} is what a test of the operand compares it with: a number, or null. */
  private static boolean against(Expression e, boolean divisor) {
    return divisor
        ? e.resolveConstantExpressionValue() instanceof Number
            || e.resolveConstantExpressionValue() instanceof Character
        : Nodes.unparenthesized(e) instanceof NullLiteral;
  }

  /**
   * Whether {@code a} and {@code b} are the same operand: the same variable or field by binding,
   * through the same qualifier, or else the same expression as written.
   */
  static boolean same(Expression a, Expression b) {
    a = Nodes.unparenthesized(a);
    b = Nodes.unparenthesized(b);
    IVariableBinding va = variable(a);
    IVariableBinding vb = variable(b);
    if (va != null || vb != null) {
      if (va == null || vb == null || !va.getKey().equals(vb.getKey())) {
        return false;
      }
      Expression qa = qualifier(a);
      Expression qb = qualifier(b);
      return qa == null || qb == null ? qa == qb : same(qa, qb);
    }
    return a.subtreeMatch(new ASTMatcher(), b);
  }

  private static IVariableBinding variable(Expression e) {
    if (e instanceof Name n) {
      return n.resolveBinding() instanceof IVariableBinding v ? v : null;
    }
    if (e instanceof FieldAccess f) {
      return f.resolveFieldBinding();
    }
    if (e instanceof SuperFieldAccess f) {
      return f.resolveFieldBinding();
    }
    return null;
  }

  /** The variable or expression a field is reached through; null for a local, this or a type. */
  private static Expression qualifier(Expression e) {
    if (e instanceof QualifiedName q) {
      return q.getQualifier().resolveBinding() instanceof IVariableBinding
          ? q.getQualifier()
          : null;
    }
    if (e instanceof FieldAccess f && !(f.getExpression() instanceof ThisExpression)) {
      return f.getExpression();
    }
    return null;
  }
}
