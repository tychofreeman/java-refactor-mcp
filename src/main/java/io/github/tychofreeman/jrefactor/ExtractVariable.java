package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    int lineStart = Lines.offsetOf(text, line);
    int lineEnd = text.indexOf('\n', lineStart) < 0 ? text.length() : text.indexOf('\n', lineStart);

    Expression e = find(unit, text, expression.strip(), lineStart, lineEnd, line, column);
    Statement statement = enclosingStatement(e);
    checkPlace(e, statement, expression.strip());

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
    int stmtLineStart = Lines.offsetOf(text, Lines.number(text, stmtStart));
    String before = text.substring(stmtLineStart, stmtStart);
    if (before.isBlank()) {
      String indent = before;
      edits.add(new TextEdit(stmtLineStart, stmtLineStart, indent + decl + "\n"));
    } else {
      edits.add(new TextEdit(stmtStart, stmtStart, decl + " "));
    }
    edits.add(new TextEdit(e.getStartPosition(), e.getStartPosition() + e.getLength(), name));
    Behavior.check(
        project,
        new Behavior.Move(e, statement, List.of(statement), source, "extracting " + source),
        Map.of(file, text),
        Map.of(file, edits));
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
              + (found.size() == 1 ? " starts at column " : " starts at columns ")
              + Nodes.columns(found, lineStart)
              + " of line "
              + line
              + ", not "
              + column
              + ". Pass one of them.");
    }
    if (found.size() > 1) {
      throw new Refusal(
          source
              + " occurs "
              + found.size()
              + " times on line "
              + line
              + ", at columns "
              + Nodes.columns(found, lineStart)
              + "; pass column, the one where the one you mean starts.");
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

  /**
   * Refuses an expression whose statement is not directly in a block, or one that is assigned to;
   * where it runs and what it crosses are the behavior gate's.
   */
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
}
