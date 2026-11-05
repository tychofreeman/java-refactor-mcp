package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;

/**
 * The behavior gate: a refactoring declares what its edit does to code, and this checks that what
 * the code does stays the same in its normal flow, and reports (or under preserve_exceptions
 * refuses) what changes in its exceptional flow. The rules live here, not in each tool.
 */
final class Behavior {

  private Behavior() {}

  /**
   * A piece of code an edit moves: {@code code} in the tree before the edit, inside {@code root},
   * landing at each of {@code landings} (nodes of the same tree, later in it, that it replaces or
   * goes before). No landing drops it; two or more duplicate it. {@code label} names it in a
   * refusal ("the initializer of n"), {@code verb} names the edit ("inlining"). {@code commute}
   * says the caller was told that the order of two effects does not matter (move_statement's
   * names_only).
   */
  record Move(
      ASTNode code,
      ASTNode root,
      List<? extends ASTNode> landings,
      String label,
      String verb,
      boolean commute) {

    Move(ASTNode code, ASTNode root, List<? extends ASTNode> landings, String label, String verb) {
      this(code, root, landings, label, verb, false);
    }
  }

  /**
   * Refuses a move that changes what the code does in its normal flow, or what a name in it means
   * where {@code edits} write it into {@code texts}; reports what changes in its exceptional flow.
   */
  static void check(
      SourceProject project, Move m, Map<Path, String> texts, Map<Path, List<TextEdit>> edits)
      throws IOException {
    checkFlow(project, m);
    NameCheck.require(project, texts, edits, NameCheck.meanings(List.of(m.code())), m.verb());
  }

  private static void checkFlow(SourceProject project, Move m) {
    Effects code = Effects.of(m.code());
    List<ASTNode> throwing = code.mayThrow();
    if (m.landings().isEmpty()) {
      if (code.changes()) {
        throw new Refusal(
            m.label()
                + " calls, assigns or changes a variable, and "
                + m.verb()
                + " drops it, so that would no longer happen.");
      }
      if (!throwing.isEmpty()) {
        Exceptional.change(
            project,
            m.label() + " may throw (" + throwing.get(0) + "), and " + m.verb() + " drops it",
            "keep it");
      }
      return;
    }
    if (m.landings().size() > 1 && code.changes()) {
      throw new Refusal(
          m.label()
              + " calls, assigns or changes a variable, and "
              + m.verb()
              + " puts it in "
              + m.landings().size()
              + " places, so it would run "
              + m.landings().size()
              + " times.");
    }
    for (ASTNode landing : m.landings()) {
      checkCrossed(project, m, code, throwing, landing);
      checkExits(project, m, code, throwing, landing);
      checkPlace(project, m, code, throwing, landing);
    }
  }

  /**
   * The code that runs on the other side of the moved code after the edit: between where it was and
   * where it lands, and, for a landing in a loop, the rest of the loop, which runs again after it.
   */
  private static void checkCrossed(
      SourceProject project, Move m, Effects code, List<ASTNode> throwing, ASTNode landing) {
    boolean earlier = landing.getStartPosition() < m.code().getStartPosition();
    int from;
    int to;
    if (earlier) {
      from = landing.getStartPosition();
      to = m.code().getStartPosition();
    } else {
      from = Nodes.end(m.code());
      ASTNode loop = Effects.loopOf(landing, m.root());
      to = loop != null ? Nodes.end(loop) : landing.getStartPosition();
    }
    Set<String> writes = new HashSet<>();
    for (IVariableBinding v : code.written()) {
      writes.add(v.getKey());
    }
    boolean calls =
        code.all().stream()
            .anyMatch(e ->
                !e.deferred()
                    && (e.kind() == Effects.Kind.CALL || e.kind() == Effects.Kind.CREATE));
    boolean reported = false;
    for (Effects.Effect e : Effects.of(m.root()).all()) {
      if (e.deferred()
          || e.node().getStartPosition() < from
          || e.end() > to
          || Nodes.within(e.node(), m.code())
          || (!earlier && Nodes.within(e.node(), landing))) {
        continue;
      }
      String where = line(e.node());
      if (e.changes()) {
        if (code.changes() && !m.commute()) {
          throw new Refusal(
              m.label()
                  + " calls, assigns or changes a variable, and "
                  + m.verb()
                  + " moves it past "
                  + e.node()
                  + " ("
                  + where
                  + "), so they would run in another order.");
        }
        IVariableBinding written = e.kind() == Effects.Kind.WRITE ? e.variable() : null;
        for (IVariableBinding read : code.named()) {
          if (written != null && read.getKey().equals(written.getKey())) {
            throw new Refusal(
                read.getName()
                    + " is read by "
                    + m.label()
                    + " and assigned ("
                    + where
                    + ") on the other side of where "
                    + m.verb()
                    + " puts it, so it would read another value.");
          }
        }
        if (!code.fixed() && e.kind() != Effects.Kind.WRITE && !m.commute()) {
          throw new Refusal(
              m.label()
                  + " reads state that "
                  + e.node()
                  + " ("
                  + where
                  + ") could change, and "
                  + m.verb()
                  + " moves the read past it.");
        }
        if (!throwing.isEmpty() && !reported) {
          reported = true;
          Exceptional.change(
              project,
              m.label()
                  + " may throw ("
                  + throwing.get(0)
                  + "), and "
                  + m.verb()
                  + " moves it past "
                  + where
                  + ", which changes state: an exception would now come "
                  + (earlier ? "before" : "after")
                  + " that change",
              "leave it where it is");
        }
      } else if (e.kind() == Effects.Kind.MAY_THROW && code.changes()) {
        if (!reported) {
          reported = true;
          Exceptional.change(
              project,
              e.node()
                  + " ("
                  + where
                  + ") may throw, and "
                  + m.verb()
                  + " moves "
                  + m.label()
                  + ", which changes state, "
                  + (earlier ? "before" : "after")
                  + " it: an exception would now come "
                  + (earlier ? "after" : "before")
                  + " that change",
              "leave it where it is");
        }
      } else if (e.variable() != null && writes.contains(e.variable().getKey())) {
        throw new Refusal(
            m.label()
                + " assigns "
                + e.variable().getName()
                + ", which "
                + e.node()
                + " ("
                + where
                + ") reads, and "
                + m.verb()
                + " moves the assignment past that read.");
      } else if (calls
          && (e.kind() == Effects.Kind.ELEMENT
              || (e.kind() == Effects.Kind.NAME
                  && e.variable() != null
                  && e.variable().isField()))) {
        throw new Refusal(
            m.label()
                + " calls code that could change "
                + e.node()
                + ", which is read at "
                + where
                + ", and "
                + m.verb()
                + " moves the call past that read.");
      }
    }
  }

  /**
   * An early exit (a return, or a break, continue or yield that leaves its statement) between where
   * the code was and where it lands, or the code itself being one: what crosses it runs when it did
   * not, or not when it did. An effect is refused; code that may throw, leaving the exit's guard,
   * is refused, and gaining it is reported.
   */
  private static void checkExits(
      SourceProject project, Move m, Effects code, List<ASTNode> throwing, ASTNode landing) {
    boolean earlier = landing.getStartPosition() < m.code().getStartPosition();
    int from = earlier ? landing.getStartPosition() : Nodes.end(m.code());
    int to = earlier ? m.code().getStartPosition() : landing.getStartPosition();
    for (Statement s : Nodes.all(m.root(), Statement.class)) {
      boolean crossed =
          s.getStartPosition() >= from
              && Nodes.end(s) <= to
              && !(s.getParent().getStartPosition() >= from && Nodes.end(s.getParent()) <= to);
      if (!crossed) {
        continue;
      }
      String exit = "the statement at " + line(s);
      if (Effects.leaves(s)) {
        if (code.changes()) {
          throw new Refusal(
              exit
                  + " may leave the block early, and "
                  + m.verb()
                  + " moves "
                  + m.label()
                  + ", which changes state, past it: it would run when it did not, or not"
                  + " when it did.");
        }
        if (!throwing.isEmpty()) {
          if (earlier) {
            throw new Refusal(
                m.label()
                    + " may throw ("
                    + throwing.get(0)
                    + "), and "
                    + exit
                    + ", which may leave the block early, guards it; "
                    + m.verb()
                    + " would move it above that guard. Keep it there.");
          }
          report(project, m, throwing, "after the early exit at " + line(s));
        }
      } else if (m.code() instanceof Statement own && Effects.leaves(own)) {
        if (Effects.of(s).changes()) {
          throw new Refusal(
              m.label()
                  + " may leave the block early, and "
                  + m.verb()
                  + " moves it past "
                  + exit
                  + ", which changes state: that would run when it did not, or not when it did.");
        }
        if (!Effects.of(s).mayThrow().isEmpty() && !earlier) {
          throw new Refusal(
              exit
                  + " may throw ("
                  + Effects.of(s).mayThrow().get(0)
                  + "), and "
                  + m.label()
                  + ", which may leave the block early, guards it; "
                  + m.verb()
                  + " would move the guard below it.");
        }
      }
    }
  }

  /**
   * Where the code runs before and after the edit: only sometimes, more than once or later ({@link
   * Effects#sometimes}, {@link Effects#later}), and under a catch. An effect must run where it did;
   * code in a loop that reads a variable must stay there; code that may throw keeps a guard or a
   * catch it had (the policy in docs/design.md), and gaining one is reported.
   */
  private static void checkPlace(
      SourceProject project, Move m, Effects code, List<ASTNode> throwing, ASTNode landing) {
    String was = place(m.code(), m.root());
    String now = place(landing, m.root());
    if (!Objects.equals(was, now)) {
      if (code.changes()) {
        throw new Refusal(
            m.label()
                + " calls, assigns or changes a variable, and "
                + m.verb()
                + " moves it "
                + (now != null
                    ? "where it runs only sometimes, more than once or later (" + now + ")"
                    : "from where it runs only sometimes, more than once or later (" + was + ")")
                + ", so it would run at another time, or not at all.");
      }
      if (Effects.loopOf(m.code(), m.root()) != null && !code.named().isEmpty()) {
        throw new Refusal(
            m.label()
                + " is "
                + was
                + ", and reads variables a later pass could change, so "
                + m.verb()
                + " would read them once. Name it inside the loop.");
      }
      if (!throwing.isEmpty()) {
        if (was != null) {
          throw new Refusal(
              m.label()
                  + " may throw ("
                  + throwing.get(0)
                  + ") and runs only sometimes where it is ("
                  + was
                  + "), under the guard that keeps it from throwing; "
                  + m.verb()
                  + " would move it out from under the guard. Keep it there.");
        } else {
          report(project, m, throwing, now);
        }
      }
    }
    String wasCaught = caught(m.code(), m.root());
    String nowCaught = caught(landing, m.root());
    if (!throwing.isEmpty() && !Objects.equals(wasCaught, nowCaught)) {
      if (wasCaught != null) {
        throw new Refusal(
            m.label()
                + " may throw ("
                + throwing.get(0)
                + ") "
                + wasCaught
                + ", which catches it or runs its finally; "
                + m.verb()
                + " would move it out of that try. Keep it there.");
      } else if (Objects.equals(was, now)) {
        report(project, m, throwing, nowCaught);
      }
    }
  }

  private static void report(SourceProject project, Move m, List<ASTNode> throwing, String now) {
    Exceptional.change(
        project,
        m.label()
            + " may throw ("
            + throwing.get(0)
            + "), and "
            + m.verb()
            + " moves it where it runs only sometimes, later or under a catch ("
            + now
            + "): an exception would come there, if at all",
        "leave it where it is");
  }

  /** Where {@code node} runs other than once, as {@code root} does, in words, or null. */
  private static String place(ASTNode node, ASTNode root) {
    String later = Effects.later(node, root);
    return later != null ? later : Effects.sometimes(node, root);
  }

  /**
   * "inside the try at line N" when {@code node} is in the body or the resources of a try that
   * catches or finishes, below {@code root}; else null.
   */
  private static String caught(ASTNode node, ASTNode root) {
    for (ASTNode n = node, p = node.getParent(); p != null && n != root; n = p, p = p.getParent()) {
      if (p instanceof TryStatement t
          && (n == t.getBody() || t.resources().contains(n))
          && (!t.catchClauses().isEmpty() || t.getFinally() != null)) {
        return "inside the try at " + line(t);
      }
    }
    return null;
  }

  private static String line(ASTNode n) {
    return n.getRoot() instanceof CompilationUnit u
        ? "line " + u.getLineNumber(n.getStartPosition())
        : "offset " + n.getStartPosition();
  }
}
