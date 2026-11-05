package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * extract_variable: an expression is named. A declaration of it is inserted just before its
 * statement, and the name is used in its place. The expression is found by the line it starts on
 * and its source text, and by column when the text occurs more than once on that line.
 */
final class ExtractVariable {

  private ExtractVariable() {}

  static Outcome run(
      SourceProject project,
      String rel,
      int line,
      String expression,
      String name,
      Integer column,
      String as)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name),
            () -> name + " is not a valid Java name for the new variable."));
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    int lineStart = lineStart(text, line);
    int lineEnd = text.indexOf('\n', lineStart) < 0 ? text.length() : text.indexOf('\n', lineStart);

    Expression e = find(unit, text, expression.strip(), lineStart, lineEnd, line, column);
    Statement statement = enclosingStatement(e);
    checkPlace(e, statement, expression.strip());
    checkTryResources(project, e, statement, expression.strip(), text);

    ITypeBinding type = e.resolveTypeBinding();
    if (type == null || type.isRecovered() || type.isNullType()) {
      throw new Refusal(
          "the type of "
              + expression.strip()
              + " does not resolve, so its declaration cannot be written. Declare the type first.");
    }
    if (as != null) {
      return Introduce.run(project, opened, e, name, as, type, expression.strip(), line);
    }
    checkFieldName(e, name);
    String typeName = Extract.typeName(type);
    String source = expression.strip();
    String decl = typeName + " " + name + " = " + source + ";";

    List<TextEdit> edits = new ArrayList<>();
    int stmtStart = statement.getStartPosition();
    int stmtLineStart = lineStart(text, lineOf(text, stmtStart));
    String before = text.substring(stmtLineStart, stmtStart);
    if (before.isBlank()) {
      String indent = before;
      edits.add(new TextEdit(stmtLineStart, stmtLineStart, indent + decl + "\n"));
    } else {
      edits.add(new TextEdit(stmtStart, stmtStart, decl + " "));
    }
    edits.add(new TextEdit(e.getStartPosition(), e.getStartPosition() + e.getLength(), name));
    String after = TextEdit.apply(text, edits, 0);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            after,
            List.of(),
            "extracting " + source + " as " + name,
            "Use a name the scope does not already declare, or an expression that compiles.");
    return new Outcome(
        "extracted "
            + source
            + " as "
            + name
            + ", declared at line "
            + line
            + " of "
            + rel
            + written.fixed().said(),
        written.declared());
  }

  /** The expression on {@code line} with source text {@code source}, by column if several. */
  static Expression find(
      CompilationUnit unit,
      String text,
      String source,
      int lineStart,
      int lineEnd,
      int line,
      Integer column) {
    List<Expression> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean preVisit2(ASTNode node) {
            if (node instanceof Expression x
                && x.getStartPosition() >= lineStart
                && x.getStartPosition() < lineEnd
                && text.substring(x.getStartPosition(), x.getStartPosition() + x.getLength())
                    .equals(source)) {
              found.add(x);
            }
            return true;
          }
        });
    if (found.isEmpty()) {
      throw new Refusal(
          "no expression with the source text "
              + source
              + " starts on line "
              + line
              + ". Give the line where it starts and its text exactly as written.");
    }
    if (column != null) {
      for (Expression x : found) {
        if (x.getStartPosition() - lineStart + 1 == column) {
          return x;
        }
      }
      throw new Refusal(
          source
              + " does not start at column "
              + column
              + " of line "
              + line
              + ". Give its column.");
    }
    if (found.size() > 1) {
      throw new Refusal(
          source
              + " occurs "
              + found.size()
              + " times on line "
              + line
              + "; pass column, the 1-based column where the one you mean starts.");
    }
    return found.get(0);
  }

  /** The statement that contains {@code e}, which must be directly in a block. */
  private static Statement enclosingStatement(Expression e) {
    ASTNode n = e;
    while (!(n instanceof Statement)) {
      n = n.getParent();
    }
    return (Statement) n;
  }

  /** Refuses an expression that would run other than once, before its statement, or assigned. */
  private static void checkPlace(Expression e, Statement s, String source) {
    if (!(s.getParent() instanceof Block)) {
      throw new Refusal(
          "the statement that holds "
              + source
              + " is not directly in a block. Put it in braces first, then extract.");
    }
    if (e.getParent() instanceof Assignment a && a.getLeftHandSide() == e) {
      throw new Refusal(source + " is assigned to, so it cannot be named as a value.");
    }
    String where = Effects.later(e, s);
    if (where == null) {
      where = Effects.sometimes(e, s);
    }
    if (where != null) {
      throw new Refusal(
          source
              + " is "
              + where
              + ", so it may not run where its statement does, or not once. Name it inside, or"
              + " extract it by hand.");
    }
    checkEarlierEffects(e, s, source);
    if (Effects.of(e).changes()) {
      throw new Refusal(
          source
              + " calls, assigns or changes a variable, and moving it before its statement could"
              + " change the order of those effects. Extract the statement's other parts first.");
    }
  }

  /** Refuses an effect that comes earlier in the statement: the read would move ahead of it. */
  private static void checkEarlierEffects(Expression e, Statement s, String source) {
    if (Effects.of(s).all().stream()
        .anyMatch(x -> x.changes() && x.end() <= e.getStartPosition())) {
      throw new Refusal(
          source
              + " comes after an effect before it in the same statement (a call, an assignment or"
              + " a ++ or --), so declaring it earlier would move its read ahead of that effect."
              + " Name the part of the statement before it first.");
    }
  }

  /** Refuses a name that a field of the enclosing class, or of a superclass, already has. */
  private static void checkFieldName(Expression e, String name) {
    ASTNode n = e;
    while (n != null && !(n instanceof TypeDeclaration)) {
      n = n.getParent();
    }
    if (n == null) {
      return;
    }
    for (ITypeBinding c = ((TypeDeclaration) n).resolveBinding();
        c != null;
        c = c.getSuperclass()) {
      for (IVariableBinding f : c.getDeclaredFields()) {
        if (f.getName().equals(name)) {
          throw new Refusal(
              name
                  + " hides the field "
                  + name
                  + " of "
                  + c.getName()
                  + ", so a use of the field in this method would read the new variable. Use"
                  + " another name.");
        }
      }
    }
  }

  private static boolean within(ASTNode inner, ASTNode outer) {
    if (outer == null) {
      return false;
    }
    int start = outer.getStartPosition();
    return inner.getStartPosition() >= start
        && inner.getStartPosition() + inner.getLength() <= start + outer.getLength();
  }

  /** The offset of the first character of 1-based {@code line}. */
  private static int lineStart(String text, int line) {
    int offset = 0;
    for (int i = 1; i < line; i++) {
      int next = text.indexOf('\n', offset);
      if (next < 0) {
        throw new Refusal("line " + line + " is past the end of the file.");
      }
      offset = next + 1;
    }
    return offset;
  }

  private static int lineOf(String text, int offset) {
    int line = 1;
    for (int i = 0; i < offset; i++) {
      if (text.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }

  /**
   * Reports, or under preserve_exceptions refuses, an expression that may throw taken out of the
   * resources of a try: that try's catch clauses caught what it threw, and its finally ran after.
   */
  private static void checkTryResources(
      SourceProject project, Expression e, Statement s, String source, String text) {
    if (!(s instanceof TryStatement t)
        || (t.catchClauses().isEmpty() && t.getFinally() == null)
        || Effects.of(e).mayThrow().isEmpty()) {
      return;
    }
    for (Object r : t.resources()) {
      if (within(e, (ASTNode) r)) {
        Exceptional.change(
            project,
            source
                + " may throw, and moves out of the resources of the try at line "
                + Lines.number(text, t.getStartPosition())
                + ", whose "
                + (t.catchClauses().isEmpty()
                    ? "finally no longer runs after it"
                    : "catch clauses no longer catch it"),
            "leave it in the try");
        return;
      }
    }
  }
}
