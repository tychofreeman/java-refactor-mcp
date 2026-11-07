package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;

/**
 * The edit primitives (gorefactor's names): an expression, statements, a switch case and an
 * element, found by line and written in place, re-indented, through the gate as every edit is.
 */
final class Primitives {

  private Primitives() {}

  /** replace_expression: the expression on {@code line} whose text is {@code expression}. */
  static Outcome replaceExpression(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = line(args, "line");
    String expression = args.required("expression").strip();
    String replacement = args.required("new");
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    int lineStart = lineStart(unit, line, rel);
    int lineEnd = text.indexOf('\n', lineStart) < 0 ? text.length() : text.indexOf('\n', lineStart);
    Expression e =
        ExtractVariable.find(
            unit, text, expression, lineStart, lineEnd, line, args.integer("column"));
    String after =
        text.substring(0, e.getStartPosition())
            + replacement
            + text.substring(e.getStartPosition() + e.getLength());
    return gated(
        project,
        opened,
        after,
        args,
        "replaced " + expression + " at line " + line + " of " + rel,
        "replacing " + expression);
  }

  /** insert_statements: {@code content} before or after the statement on {@code line}. */
  static Outcome insertStatements(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = line(args, "line");
    String where = args.string("where") == null ? "after" : args.string("where");
    Precondition.require(
        Precondition.that(
            () -> where.equals("before") || where.equals("after"),
            () -> "where is before or after: where the statements go."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    Statement s =
        opened.startingOn(
            line,
            Statement.class,
            x -> x.getParent() instanceof Block || x.getParent() instanceof SwitchStatement,
            "statement",
            ", directly in a block",
            "the statement");
    String block = indent(args.required("content"), Lines.indentOf(text, s.getStartPosition()));
    int at =
        where.equals("before")
            ? Lines.start(text, s.getStartPosition())
            : Lines.end(text, Nodes.end(s) - 1);
    String after =
        text.substring(0, at) + block + "\n" + text.substring(Math.min(at, text.length()));
    int count = block.split("\n", -1).length;
    return gated(
        project,
        opened,
        after,
        args,
        "inserted "
            + count
            + (count == 1 ? " line " : " lines ")
            + where
            + " the statement at line "
            + line
            + " of "
            + rel,
        "inserting statements");
  }

  /**
   * replace_statements: the whole statements of one block from {@code start_line} to {@code
   * end_line}, with {@code content}; empty content deletes them.
   */
  static Outcome replaceStatements(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int startLine = line(args, "start_line");
    int endLine = line(args, "end_line");
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    Statement first =
        opened.startingOn(
            startLine,
            Statement.class,
            x -> x.getParent() instanceof Block,
            "statement",
            ", directly in a block",
            "the first statement");
    Block block = (Block) first.getParent();
    Statement last = null;
    List<String> ends = new ArrayList<>();
    boolean seen = false;
    for (Object o : block.statements()) {
      Statement s = (Statement) o;
      seen |= s == first;
      if (!seen) {
        continue;
      }
      int end = Lines.number(text, Nodes.end(s) - 1);
      ends.add(String.valueOf(end));
      if (end == endLine) {
        last = s;
        break;
      }
    }
    if (last == null) {
      throw new Refusal(
          "line "
              + endLine
              + " does not end a statement of the block that line "
              + startLine
              + " starts in; its statements from there end on lines "
              + String.join(", ", ends)
              + ". Pass one of those.");
    }
    int start = Lines.start(text, first.getStartPosition());
    int end = Lines.end(text, Nodes.end(last) - 1);
    String content = args.string("content") == null ? "" : args.string("content");
    String surround = args.string("surround");
    if (args.string("content") == null && surround == null) {
      throw new Refusal(
          "replace_statements needs content (empty deletes the statements) or surround.");
    }
    if (surround != null) {
      if (!content.isEmpty()) {
        throw new Refusal("replace_statements takes content or surround, not both.");
      }
      String outer = Lines.indentOf(text, block.getStartPosition());
      String inner = Lines.indentOf(text, first.getStartPosition());
      String step = inner.length() > outer.length() ? inner.substring(outer.length()) : "  ";
      List<Statement> statements = new ArrayList<>();
      boolean in = false;
      for (Object o : block.statements()) {
        in |= o == first;
        if (in) {
          statements.add((Statement) o);
        }
        if (o == last) {
          break;
        }
      }
      ASTNode method = block;
      while (method.getParent() != null && !(method instanceof BodyDeclaration)) {
        method = method.getParent();
      }
      content =
          Surround.wrap(
              surround,
              text.substring(start, end).stripTrailing(),
              step,
              statements,
              text.substring(method.getStartPosition(), Nodes.end(method)));
    }
    if (project.session().skeletonOnly && surround == null && !content.isBlank()) {
      Generated.checkSurround(text.substring(start, end), content);
    }
    String after;
    String said;
    if (content.isBlank()) {
      after = text.substring(0, start) + text.substring(end);
      said = "deleted lines " + startLine + "-" + endLine + " of " + rel;
    } else {
      String replaced = indent(content, Lines.indentOf(text, first.getStartPosition()));
      after = text.substring(0, start) + replaced + "\n" + text.substring(end);
      int count = replaced.split("\n", -1).length;
      said =
          "replaced lines "
              + startLine
              + "-"
              + endLine
              + " of "
              + rel
              + " with "
              + count
              + (count == 1 ? " line" : " lines");
    }
    if (project.session().skeletonOnly && block.getParent() instanceof MethodDeclaration m) {
      // The whole body of a member: what it becomes is checked as add_declaration checks a member.
      for (MethodDeclaration now : Nodes.all(Edits.parses(after, rel), MethodDeclaration.class)) {
        if (now.getStartPosition() == m.getStartPosition()) {
          Generated.checkMembers(
              List.of(now),
              " in an add_declaration in place of this replace_statements; it replaces the "
                  + (m.isConstructor() ? "constructor." : "method."));
        }
      }
    }
    return gated(project, opened, after, args, said, "replacing statements");
  }

  /** add_case: {@code content} as a case of the switch on {@code line}, before its default. */
  static Outcome addCase(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = line(args, "line");
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    ASTNode sw =
        opened.startingOn(
            line,
            ASTNode.class,
            n -> n instanceof SwitchStatement || n instanceof SwitchExpression,
            "switch",
            "",
            "the switch");
    List<?> statements =
        sw instanceof SwitchStatement s ? s.statements() : ((SwitchExpression) sw).statements();
    SwitchCase firstCase = null;
    SwitchCase defaultCase = null;
    for (Object o : statements) {
      if (o instanceof SwitchCase c) {
        firstCase = firstCase == null ? c : firstCase;
        if (c.isDefault()) {
          defaultCase = c;
        }
      }
    }
    int closing = Nodes.end(sw) - 1;
    String indent =
        firstCase != null
            ? Lines.indentOf(text, firstCase.getStartPosition())
            : Lines.indentOf(text, sw.getStartPosition()) + "  ";
    int at =
        defaultCase != null
            ? Lines.start(text, defaultCase.getStartPosition())
            : Lines.start(text, closing);
    String after =
        text.substring(0, at)
            + indent(args.required("content"), indent)
            + "\n"
            + text.substring(at);
    return gated(
        project,
        opened,
        after,
        args,
        "added a case to the switch at line "
            + line
            + " of "
            + rel
            + (defaultCase != null ? ", before its default" : ", at its end"),
        "adding a case");
  }

  /**
   * add_element: {@code content} as the last element of the array initializer or enum on {@code
   * line}.
   */
  static Outcome addElement(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    int line = line(args, "line");
    String content = args.required("content").strip();
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    ASTNode node =
        opened.startingOn(
            line,
            ASTNode.class,
            n -> n instanceof ArrayInitializer || n instanceof EnumDeclaration,
            "array initializer or enum",
            "",
            "it");
    List<?> elements =
        node instanceof ArrayInitializer a
            ? a.expressions()
            : ((EnumDeclaration) node).enumConstants();
    String after;
    if (elements.isEmpty()) {
      int open = text.indexOf('{', node.getStartPosition());
      after = text.substring(0, open + 1) + content + text.substring(open + 1);
    } else {
      ASTNode lastElement = (ASTNode) elements.get(elements.size() - 1);
      int end = Nodes.end(lastElement);
      boolean ownLine =
          elements.size() > 1
              && Lines.number(
                      text, ((ASTNode) elements.get(elements.size() - 2)).getStartPosition())
                  != Lines.number(text, lastElement.getStartPosition());
      String sep = ownLine ? ",\n" + Lines.indentOf(text, lastElement.getStartPosition()) : ", ";
      after = text.substring(0, end) + sep + content + text.substring(end);
    }
    return gated(
        project,
        opened,
        after,
        args,
        "added "
            + content
            + " to the "
            + (node instanceof EnumDeclaration ? "enum" : "array")
            + " at line "
            + line
            + " of "
            + rel,
        "adding an element");
  }

  private static Outcome gated(
      SourceProject project, Opened opened, String after, Args args, String said, String what)
      throws Exception {
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            opened.text(),
            after,
            args.list("imports"),
            what,
            "Nothing was changed; write it so that it compiles there.");
    return new Outcome(said + written.fixed().said(), written.declared());
  }

  private static int line(Args args, String key) {
    Integer line = args.integer(key);
    if (line == null) {
      throw new Refusal(args.tool() + " needs " + key + ", a 1-based line.");
    }
    return line;
  }

  private static int lineStart(CompilationUnit unit, int line, String rel) {
    int at = unit.getPosition(line, 0);
    if (at < 0) {
      throw new Refusal(rel + " has no line " + line + ".");
    }
    return at;
  }

  /** {@code content}'s lines with their common indentation replaced by {@code indent}. */
  static String indent(String content, String indent) {
    String[] lines = content.stripTrailing().split("\n", -1);
    int common = Integer.MAX_VALUE;
    for (String l : lines) {
      if (!l.isBlank()) {
        common = Math.min(common, l.length() - l.stripLeading().length());
      }
    }
    common = common == Integer.MAX_VALUE ? 0 : common;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      if (!lines[i].isBlank()) {
        out.append(indent).append(lines[i].substring(Math.min(common, lines[i].length())));
      }
    }
    return out.toString();
  }
}
