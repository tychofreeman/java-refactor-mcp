package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
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
}
