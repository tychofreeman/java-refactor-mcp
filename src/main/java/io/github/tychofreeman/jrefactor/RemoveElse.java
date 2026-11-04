package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/**
 * remove_else: an if whose then-branch cannot finish normally loses its else, and the else's
 * statements follow the if. The then-branch must end in a return, throw, break, continue or yield;
 * otherwise the else's statements would run after it as well. The if must be a statement of a
 * block, since its else's statements would land outside a branch that has no braces.
 */
final class RemoveElse {

  private RemoveElse() {}

  static Outcome run(SourceProject project, String rel, Integer line) throws Exception {
    if (line == null) {
      throw new Refusal("remove_else needs line, the 1-based line the if starts on.");
    }
    Path file = project.javaFile(rel);
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    List<IfStatement> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean preVisit2(ASTNode node) {
            if (node instanceof IfStatement s && Lines.number(text, s.getStartPosition()) == line) {
              found.add(s);
            }
            return true;
          }
        });
    if (found.isEmpty()) {
      throw new Refusal(
          "no if statement starts on line "
              + line
              + " of "
              + rel
              + ". Pass the line where the if starts.");
    }
    if (found.size() > 1) {
      throw new Refusal(
          "several if statements start on line "
              + line
              + " of "
              + rel
              + ". Put each on a line of its own first.");
    }
    IfStatement ifs = found.get(0);
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
    if (canFinish(ifs.getThenStatement())) {
      throw new Refusal(
          "the then-branch of the if at line "
              + line
              + " can finish normally, so the else's statements would run after it as well. Put"
              + " the else's statements in the then-branch, or end the then-branch with a return,"
              + " throw, break or continue.");
    }

    int start = ifs.getStartPosition();
    int end = start + ifs.getLength();
    String base = leadingWhitespace(text, Lines.start(text, start));
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

  private static boolean canFinish(Statement then) {
    Statement last = then;
    if (then instanceof Block block) {
      if (block.statements().isEmpty()) {
        return true;
      }
      last = (Statement) block.statements().get(block.statements().size() - 1);
    }
    return !(last instanceof ReturnStatement
        || last instanceof ThrowStatement
        || last instanceof BreakStatement
        || last instanceof ContinueStatement
        || last instanceof YieldStatement);
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
        delta = Math.max(0, leadingSpaces(l) - base.length());
        break;
      }
    }
    List<String> out = new ArrayList<>();
    for (String l : lines) {
      if (l.isBlank()) {
        out.add("");
      } else {
        out.add(l.substring(Math.min(delta, leadingSpaces(l))));
      }
    }
    return String.join("\n", out);
  }

  private static int leadingSpaces(String line) {
    int n = 0;
    while (n < line.length() && line.charAt(n) == ' ') {
      n++;
    }
    return n;
  }

  private static String leadingWhitespace(String text, int lineStart) {
    int i = lineStart;
    while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
      i++;
    }
    return text.substring(lineStart, i);
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
  }
}
