package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * remove_else: an if whose then-branch cannot finish normally loses its else, and the else's
 * statements follow the if. The then-branch must end in a return, throw, break, continue or yield;
 * otherwise the else's statements would run after it as well. The if must be a statement of a
 * block, since its else's statements would land outside a branch that has no braces.
 */
final class RemoveElse {

  private RemoveElse() {}

  static Outcome run(SourceProject project, String rel, Integer line) throws Exception {
    Precondition.require(
        Precondition.that(
            () -> line != null,
            () -> "remove_else needs line, the 1-based line the if starts on."));
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    IfStatement ifs =
        opened.startingOn(line, IfStatement.class, s -> true, "if statement", "", "the if");
    Statement elseBranch = ifs.getElseStatement();
    if (elseBranch == null) {
      throw new Refusal("the if at line " + line + " has no else, so there is nothing to remove.");
    }
    if (!(ifs.getParent() instanceof Block)) {
      throw new Refusal(
          "the if at line "
              + line
              + " is not directly in a block (it is the body of another statement without"
              + " braces). Put braces around that body first, or the else's statements would land"
              + " outside it.");
    }
    if (Effects.completesNormally(ifs.getThenStatement())) {
      throw new Refusal(
          "the then-branch of the if at line "
              + line
              + " can finish normally, so the else's statements would run after it as well. Put"
              + " the else's statements in the then-branch, or end the then-branch with a return,"
              + " throw, break or continue.");
    }

    String hidden = namedAfter(ifs, elseBranch);
    if (hidden != null) {
      throw new Refusal(
          hidden
              + ", declared in the else, is named after the if at line "
              + line
              + ": once the else is removed, the name there would mean that local. Rename the"
              + " local first (rename with its line), then remove the else.");
    }

    int start = ifs.getStartPosition();
    int end = start + ifs.getLength();
    String base = Lines.indentOf(text, Lines.start(text, start));
    String replacement =
        "if ("
            + source(text, ifs.getExpression())
            + ") "
            + source(text, ifs.getThenStatement())
            + "\n"
            + elseStatements(text, elseBranch, base);
    String updated = text.substring(0, start) + replacement + text.substring(end);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            updated,
            List.of(),
            "removing the else at line " + line,
            "Check that the else's statements still compile where they now follow the if.");
    return new Outcome(
        "removed the else of the if at line " + line + " in " + rel + written.fixed().said(),
        written.declared());
  }

  /**
   * The else's statements at the if's indentation. A block's own lines are dedented by the amount
   * its first statement is indented past the if's line.
   */
  private static String elseStatements(String text, Statement elseBranch, String base) {
    if (!(elseBranch instanceof Block)) {
      return base + source(text, elseBranch);
    }
    String inner = source(text, elseBranch);
    String body = inner.substring(1, inner.length() - 1);
    if (!body.contains("\n")) {
      return base + body.strip();
    }
    List<String> lines = new ArrayList<>(List.of(body.split("\n", -1)));
    if (lines.get(0).isBlank()) {
      lines.remove(0);
    }
    if (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
      lines.remove(lines.size() - 1);
    }
    int delta = 0;
    for (String l : lines) {
      if (!l.isBlank()) {
        delta = Math.max(0, Indent.width(l) - base.length());
        break;
      }
    }
    List<String> out = new ArrayList<>();
    for (String l : lines) {
      if (l.isBlank()) {
        out.add("");
      } else {
        out.add(l.substring(Math.min(delta, Indent.width(l))));
      }
    }
    return String.join("\n", out);
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
  }

  /** A local or class the else block declares that a statement after the if names, or null. */
  private static String namedAfter(IfStatement ifs, Statement elseBranch) {
    if (!(elseBranch instanceof Block block) || !(ifs.getParent() instanceof Block outer)) {
      return null;
    }
    List<String> declared = new ArrayList<>();
    for (Object o : block.statements()) {
      if (o instanceof VariableDeclarationStatement v) {
        for (Object f : v.fragments()) {
          declared.add(((VariableDeclarationFragment) f).getName().getIdentifier());
        }
      } else if (o instanceof TypeDeclarationStatement t) {
        declared.add(t.getDeclaration().getName().getIdentifier());
      }
    }
    List<?> all = outer.statements();
    for (Object later : all.subList(all.indexOf(ifs) + 1, all.size())) {
      for (SimpleName n : Nodes.all((ASTNode) later, SimpleName.class)) {
        if (declared.contains(n.getIdentifier())) {
          return n.getIdentifier();
        }
      }
    }
    return null;
  }
}
