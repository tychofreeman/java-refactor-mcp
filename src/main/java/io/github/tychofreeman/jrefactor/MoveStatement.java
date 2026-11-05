package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/**
 * move_statement: a statement trades places with the one before or after it in the same block. The
 * two must be independent: they share no name that one of them writes, and, unless names_only is
 * set, at most one of them has a side effect (a call, an assignment, a new object), so the order
 * they run in does not change what happens. A return, break, continue, throw or yield never moves.
 */
final class MoveStatement {

  private MoveStatement() {}

  static Outcome run(
      SourceProject project, String rel, Integer line, String direction, boolean namesOnly)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> line != null,
            () -> "move_statement needs line, the 1-based line the statement starts on."),
        Precondition.that(
            () -> "up".equals(direction) || "down".equals(direction),
            () -> "direction is up or down, the way the statement moves."));
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    Statement moved =
        opened.startingOn(
            line,
            Statement.class,
            s -> s.getParent() instanceof Block,
            "statement",
            ", directly in a block",
            "the statement");
    if (isJump(moved)) {
      throw new Refusal(
          "the statement at line "
              + line
              + " is a return, break, continue, throw or yield, which changes where the code"
              + " goes. Move the statements around it instead.");
    }
    Block block = (Block) moved.getParent();
    List<Statement> statements = new ArrayList<>();
    for (Object o : block.statements()) {
      statements.add((Statement) o);
    }
    int i = statements.indexOf(moved);
    int j = "up".equals(direction) ? i - 1 : i + 1;
    if (j < 0 || j >= statements.size()) {
      throw new Refusal(
          "the statement at line "
              + line
              + " has no statement "
              + ("up".equals(direction) ? "above" : "below")
              + " it in its block to swap with.");
    }
    Statement other = statements.get(j);
    if (isJump(other)) {
      throw new Refusal(
          "the statement at line "
              + Lines.number(text, other.getStartPosition())
              + " is a return, break, continue, throw or yield, so nothing moves past it.");
    }
    Statement early = j < i ? other : moved;
    Statement late = j < i ? moved : other;
    Effects a = Effects.of(early);
    Effects b = Effects.of(late);
    String lines =
        "the statements at lines "
            + Lines.number(text, early.getStartPosition())
            + " and "
            + Lines.number(text, late.getStartPosition());
    if (intersects(a.written(), new HashSet<>(b.named()))
        || intersects(b.written(), new HashSet<>(a.named()))) {
      throw new Refusal(
          lines
              + " share a name: one declares or writes a variable or field the other uses. Moving"
              + " them past each other would change what the later one reads. Move the statement"
              + " another way, or extract the other one first.");
    }
    if (!namesOnly && a.changes() && b.changes()) {
      throw new Refusal(
          lines
              + " both have side effects (a call, an assignment or a new object), so swapping them"
              + " changes the order they run in. Pass names_only=true when the two share no name"
              + " that one writes.");
    }

    checkGuard(project, early, late, a, b, text);
    checkExceptional(project, early, late, a, b, text);
    int s1 = early.getStartPosition();
    int e1 = s1 + early.getLength();
    int s2 = late.getStartPosition();
    int e2 = s2 + late.getLength();
    String swapped =
        text.substring(0, s1)
            + text.substring(s2, e2)
            + text.substring(e1, s2)
            + text.substring(s1, e1)
            + text.substring(e2);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            swapped,
            List.of(),
            "moving the statement at line " + line + " " + direction,
            "Check that the names the statement uses still resolve where it lands.");
    return new Outcome(
        "moved the statement at line "
            + line
            + " "
            + direction
            + ", past the statement at line "
            + Lines.number(text, other.getStartPosition())
            + " in "
            + rel
            + written.fixed().said(),
        written.declared());
  }

  /**
   * Reports, or under preserve_exceptions refuses, a swap of a statement that may throw with one
   * that changes state: the exception would come after that change, or stop it.
   */
  private static void checkExceptional(
      SourceProject project, Statement early, Statement late, Effects a, Effects b, String text) {
    int earlyLine = Lines.number(text, early.getStartPosition());
    int lateLine = Lines.number(text, late.getStartPosition());
    String change = null;
    if (!a.mayThrow().isEmpty() && b.changes()) {
      change =
          "the statement at line "
              + earlyLine
              + " may throw ("
              + a.mayThrow().get(0)
              + ") and now runs after the one at line "
              + lateLine
              + ", which changes state: an exception would now come after that change";
    } else if (!b.mayThrow().isEmpty() && a.changes()) {
      change =
          "the statement at line "
              + lateLine
              + " may throw ("
              + b.mayThrow().get(0)
              + ") and now runs before the one at line "
              + earlyLine
              + ", which changes state: an exception would now stop that change";
    }
    if (change != null) {
      Exceptional.change(project, change, "move the statements another way");
    }
  }

  /**
   * Whether {@code s} holds a return, or a yield, break or continue that leaves it, outside lambdas
   * and classes declared in it.
   */
  private static boolean leaves(Statement s) {
    boolean[] found = {false};
    s.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(LambdaExpression n) {
            return false;
          }

          @Override
          public boolean visit(AnonymousClassDeclaration n) {
            return false;
          }

          @Override
          public boolean visit(TypeDeclarationStatement n) {
            return false;
          }

          @Override
          public boolean visit(ReturnStatement n) {
            found[0] = true;
            return false;
          }

          @Override
          public boolean visit(YieldStatement n) {
            found[0] |= !caught(n, null, false, s);
            return false;
          }

          @Override
          public boolean visit(BreakStatement n) {
            found[0] |= !caught(n, n.getLabel(), true, s);
            return false;
          }

          @Override
          public boolean visit(ContinueStatement n) {
            found[0] |= !caught(n, n.getLabel(), false, s);
            return false;
          }
        });
    return found[0];
  }

  /**
   * Whether the jump {@code n} lands inside {@code s}: on a statement labeled {@code label}, or,
   * unlabeled, on a loop (a switch too for a break; a switch expression for a yield).
   */
  private static boolean caught(Statement n, SimpleName label, boolean isBreak, Statement s) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (n instanceof YieldStatement) {
        if (p instanceof SwitchExpression) {
          return true;
        }
      } else if (label != null) {
        if (p instanceof LabeledStatement l
            && l.getLabel().getIdentifier().equals(label.getIdentifier())) {
          return true;
        }
      } else if (p instanceof ForStatement
          || p instanceof EnhancedForStatement
          || p instanceof WhileStatement
          || p instanceof DoStatement
          || (isBreak && p instanceof SwitchStatement)) {
        return true;
      }
      if (p == s) {
        return false;
      }
    }
    return false;
  }

  private static boolean isJump(Statement s) {
    return s instanceof ReturnStatement
        || s instanceof BreakStatement
        || s instanceof ContinueStatement
        || s instanceof ThrowStatement
        || s instanceof YieldStatement;
  }

  private static boolean intersects(Set<IVariableBinding> a, Set<IVariableBinding> b) {
    for (IVariableBinding v : a) {
      if (b.contains(v)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Refuses swapping a statement that changes state with one that may leave the block early (a
   * return, yield, break or continue in it that leaves it): it would run when it did not, or not
   * when it did. One that may throw instead is reported, or refused under preserve_exceptions.
   */
  private static void checkGuard(
      SourceProject project, Statement early, Statement late, Effects a, Effects b, String text) {
    Statement guard = leaves(early) ? early : leaves(late) ? late : null;
    if (guard == null) {
      return;
    }
    Statement other = guard == early ? late : early;
    Effects effects = guard == early ? b : a;
    String at =
        "the statement at line "
            + Lines.number(text, guard.getStartPosition())
            + " may leave the block early, and the one at line "
            + Lines.number(text, other.getStartPosition());
    if (effects.changes()) {
      throw new Refusal(
          at
              + " changes state (a call, an assignment or a new object), so swapping them would"
              + " make it run when it did not, or not run when it did. Move it another way.");
    }
    if (!effects.mayThrow().isEmpty()) {
      Exceptional.change(
          project,
          at
              + " may throw ("
              + effects.mayThrow().get(0)
              + "): it would now throw where the block was left before",
          "move the statements another way");
    }
  }
}
