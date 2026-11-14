package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Statement;

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
      String as,
      boolean all)
      throws Exception {
    Precondition.require(Precondition.identifier(name, "the new variable"));
    if (all && !"parameter".equals(as)) {
      throw new Refusal(
          "all_occurrences is built for as=parameter, where each occurrence of "
              + expression.strip()
              + " in the method becomes the new parameter. Leave it out, and extract one"
              + " occurrence at a time.");
    }
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    Expression e = find(opened, expression.strip(), line, column);
    Statement statement = Nodes.ancestorOrSelf(e, Statement.class);
    if (statement == null && !"constant".equals(as)) {
      throw new Refusal(
          expression.strip()
              + " is not inside a statement (it is in a field's initializer, say): a local has no"
              + " place before it, and a new field would be declared after the field that reads it."
              + " Extract it with as=constant.");
    }
    if (statement != null) {
      checkPlace(e, statement, expression.strip());
    }

    ITypeBinding type = e.resolveTypeBinding();
    if (type == null || type.isRecovered() || type.isNullType()) {
      throw new Refusal(
          "the type of "
              + expression.strip()
              + " does not resolve, so its declaration cannot be written. Declare the type first.");
    }
    if (as != null) {
      return Introduce.run(project, opened, e, name, as, type, expression.strip(), line, all);
    }
    checkFieldName(e, name);
    String typeName =
        e instanceof LambdaExpression || e instanceof MethodReference
            ? Extract.groundTypeName(type)
            : Extract.typeName(type);
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
    edits.add(new TextEdit(e.getStartPosition(), Nodes.end(e), name));
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
  static Expression find(Opened opened, String source, int line, Integer column) {
    String text = opened.text();
    List<Expression> found = new ArrayList<>();
    for (Expression x : opened.onLine(line, Expression.class)) {
      if (Nodes.source(text, x).equals(source)) {
        found.add(x);
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          "no expression with the source text "
              + source
              + " starts on line "
              + line
              + ". Give the line where it starts and its text exactly as written.");
    }
    found = opened.atColumn(found, line, column, source);
    if (found.size() > 1) {
      throw opened.several(found, line, "occurrences of " + source);
    }
    return found.get(0);
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

  /**
   * Refuses a name that a field of a type around the expression, or of one of its supertypes,
   * already has: a local of that name hides the field from the rest of the method.
   */
  private static void checkFieldName(Expression e, String name) {
    for (ASTNode n = e; n != null; n = n.getParent()) {
      ITypeBinding type =
          n instanceof AbstractTypeDeclaration t
              ? t.resolveBinding()
              : n instanceof AnonymousClassDeclaration a ? a.resolveBinding() : null;
      if (type == null) {
        continue;
      }
      for (ITypeBinding c : Hierarchy.supertypes(type, true)) {
        for (IVariableBinding f : c.getDeclaredFields()) {
          if (f.getName().equals(name)) {
            throw new Refusal(
                name
                    + " hides the field "
                    + name
                    + " of "
                    + (c.isAnonymous() ? "an anonymous class" : c.getName())
                    + ", so a use of the field in this method would read the new variable. Use"
                    + " another name.");
          }
        }
      }
    }
  }
}
