package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
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
   * refusal ("the initializer of n"), {@code verb} names the edit ("inlining").
   */
  record Move(
      ASTNode code, ASTNode root, List<? extends ASTNode> landings, String label, String verb) {}

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
    int from = Nodes.end(m.code());
    for (ASTNode landing : m.landings()) {
      checkCrossed(project, m, code, throwing, from, landing);
      checkPlace(project, m, code, throwing, landing);
    }
  }

  /** The effects between where the code was and where it lands, which it now runs after. */
  private static void checkCrossed(
      SourceProject project,
      Move m,
      Effects code,
      List<ASTNode> throwing,
      int from,
      ASTNode landing) {
    for (Effects.Effect e : Effects.of(m.root()).all()) {
      if (e.deferred()
          || e.node().getStartPosition() < from
          || e.end() > landing.getStartPosition()
          || !e.changes()) {
        continue;
      }
      String where = line(e.node());
      if (code.changes()) {
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
                  + " and assigned after the declaration ("
                  + where
                  + "), before a use, so "
                  + m.verb()
                  + " would change the value it reads. Keep the variable.");
        }
      }
      if (!code.fixed() && e.kind() != Effects.Kind.WRITE) {
        throw new Refusal(
            m.label()
                + " reads state that "
                + e.node()
                + " ("
                + where
                + ") could change, and "
                + m.verb()
                + " moves the read after it. Keep the variable.");
      }
      if (!throwing.isEmpty()) {
        Exceptional.change(
            project,
            m.label()
                + " may throw ("
                + throwing.get(0)
                + "), and "
                + m.verb()
                + " moves it past "
                + where
                + ", which changes state: an exception would now come after that change",
            "keep the variable, or move that statement above the declaration first");
        return;
      }
    }
  }

  /** Where the code lands: code that runs only sometimes, later, or under a catch. */
  private static void checkPlace(
      SourceProject project, Move m, Effects code, List<ASTNode> throwing, ASTNode landing) {
    String where = Effects.later(landing, m.root());
    if (where == null) {
      where = Effects.sometimes(landing, m.root());
    }
    if (where != null && code.changes()) {
      throw new Refusal(
          m.label()
              + " calls, assigns or changes a variable, and "
              + m.verb()
              + " moves it where it runs only sometimes, more than once or later ("
              + where
              + "), so it would run at another time, or not at all. Keep the variable.");
    }
    if (throwing.isEmpty()) {
      return;
    }
    if (where == null) {
      where = caught(landing, m.root());
    }
    if (where != null) {
      Exceptional.change(
          project,
          m.label()
              + " may throw ("
              + throwing.get(0)
              + "), and "
              + m.verb()
              + " moves it where it runs only sometimes, later or under a catch ("
              + where
              + "): an exception would come there, if at all",
          "keep the variable");
    }
  }

  /** "inside the try at line N" when {@code node} is in a try body that catches or finishes. */
  private static String caught(ASTNode node, ASTNode root) {
    for (ASTNode n = node, p = node.getParent(); p != null && n != root; n = p, p = p.getParent()) {
      if (p instanceof TryStatement t
          && n == t.getBody()
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
