package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * invert_if: an if with an else swaps its branches and negates its condition. A comparison is
 * negated by its opposite operator, a !x loses its !, and any other condition is wrapped in !. An
 * ordering comparison on a float or double is refused: !(a &lt; b) is not a &gt;= b when a value is
 * NaN. An else-if chain is refused, since swapping it would leave a dangling else; any other else
 * branch that ends in an if without an else is put in braces.
 */
final class InvertIf {

  private InvertIf() {}

  static Outcome run(SourceProject project, String rel, Integer line) throws Exception {
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    IfStatement ifs =
        opened.startingOn(line, IfStatement.class, s -> true, "if statement", "", "the if");
    Statement elseBranch = ifs.getElseStatement();
    if (elseBranch == null) {
      throw new Refusal(
          "the if at line "
              + line
              + " has no else, so there is no branch to swap with. Add an else first.");
    }
    if (elseBranch instanceof IfStatement) {
      throw new Refusal(
          "the if at line "
              + line
              + " is an else if chain. Invert the inner if, which starts on its own line,"
              + " instead.");
    }
    String condition = negate(text, ifs.getExpression(), line);
    String replacement =
        "if ("
            + condition
            + ") "
            + thenText(text, elseBranch, ifs)
            + " else "
            + Nodes.source(text, ifs.getThenStatement());
    int start = ifs.getStartPosition();
    int end = start + ifs.getLength();
    String updated = text.substring(0, start) + replacement + text.substring(end);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            updated,
            List.of(),
            "inverting the if at line " + line,
            "Check that both branches still compile with the negated condition.");
    return new Outcome(
        "inverted the if at line "
            + line
            + " in "
            + rel
            + "; its condition is now "
            + condition
            + written.fixed().said(),
        written.declared());
  }

  static String negate(String text, Expression cond, int line) {
    if (cond instanceof PrefixExpression p && p.getOperator() == PrefixExpression.Operator.NOT) {
      return Nodes.source(text, p.getOperand());
    }
    if (cond instanceof InfixExpression in && in.extendedOperands().isEmpty()) {
      String op = opposite(in.getOperator());
      if (op != null) {
        if (ordering(in.getOperator())
            && (floating(in.getLeftOperand()) || floating(in.getRightOperand()))) {
          throw new Refusal(
              "the condition at line "
                  + line
                  + " compares a float or double with an ordering operator. Its negation is not"
                  + " the opposite operator when a value is NaN, so the if is not inverted. Write"
                  + " the condition by hand, or use == and != instead.");
        }
        return Nodes.source(text, in.getLeftOperand())
            + " "
            + op
            + " "
            + Nodes.source(text, in.getRightOperand());
      }
    }
    String src = Nodes.source(text, cond);
    return primary(cond) ? "!" + src : "!(" + src + ")";
  }

  private static String opposite(InfixExpression.Operator op) {
    if (op == InfixExpression.Operator.EQUALS) {
      return "!=";
    }
    if (op == InfixExpression.Operator.NOT_EQUALS) {
      return "==";
    }
    if (op == InfixExpression.Operator.LESS) {
      return ">=";
    }
    if (op == InfixExpression.Operator.GREATER_EQUALS) {
      return "<";
    }
    if (op == InfixExpression.Operator.GREATER) {
      return "<=";
    }
    if (op == InfixExpression.Operator.LESS_EQUALS) {
      return ">";
    }
    return null;
  }

  private static boolean ordering(InfixExpression.Operator op) {
    return op == InfixExpression.Operator.LESS
        || op == InfixExpression.Operator.LESS_EQUALS
        || op == InfixExpression.Operator.GREATER
        || op == InfixExpression.Operator.GREATER_EQUALS;
  }

  private static boolean floating(Expression e) {
    ITypeBinding t = e.resolveTypeBinding();
    if (t == null) {
      return false;
    }
    String n = t.getName();
    return n.equals("float") || n.equals("double") || n.equals("Float") || n.equals("Double");
  }

  /** A condition that a ! can be put before without parentheses. */
  private static boolean primary(Expression e) {
    return e instanceof Name
        || e instanceof MethodInvocation
        || e instanceof FieldAccess
        || e instanceof ParenthesizedExpression;
  }

  /**
   * The old else branch as the new then branch: in braces when it is unbraced and ends in an if
   * without an else, which the swapped else would otherwise attach to.
   */
  private static String thenText(String text, Statement branch, IfStatement ifs) {
    String src = Nodes.source(text, branch);
    if (!endsInOpenIf(branch)) {
      return src;
    }
    String indent = Lines.indentOf(text, ifs.getStartPosition());
    return "{\n" + indent + ThrowsCallers.step(text) + src + "\n" + indent + "}";
  }

  /** Whether the last statement {@code s} ends with is an if with no else. */
  private static boolean endsInOpenIf(Statement s) {
    if (s instanceof IfStatement i) {
      return i.getElseStatement() == null || endsInOpenIf(i.getElseStatement());
    }
    if (s instanceof ForStatement f) {
      return endsInOpenIf(f.getBody());
    }
    if (s instanceof EnhancedForStatement f) {
      return endsInOpenIf(f.getBody());
    }
    if (s instanceof WhileStatement w) {
      return endsInOpenIf(w.getBody());
    }
    if (s instanceof LabeledStatement l) {
      return endsInOpenIf(l.getBody());
    }
    return false;
  }
}
