package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * inline_variable: a local variable is replaced by its initializer at each use, and its declaration
 * is deleted. The inverse of extract_variable. A variable is named by a line on which it is
 * declared or used, and its name.
 */
final class InlineVariable {

  private InlineVariable() {}

  static Outcome run(SourceProject project, String rel, int line, String name, Integer column)
      throws Exception {
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    IVariableBinding variable = variableAt(unit, text, name, line, column);
    if (variable.isField()) {
      throw new Refusal(
          name
              + " is a field. inline_variable inlines a local variable; a field needs its uses"
              + " changed another way.");
    }
    if (variable.isParameter()) {
      throw new Refusal(
          name
              + " is a parameter. inline_variable inlines a local variable declared with an"
              + " initializer.");
    }
    VariableDeclarationFragment fragment = declarationOf(unit, variable, name);
    if (!(fragment.getParent() instanceof VariableDeclarationStatement stmt)) {
      throw new Refusal(
          name
              + " is declared in a for loop or a pattern, not as a statement. Inline a local"
              + " declared as a statement.");
    }
    if (stmt.fragments().size() != 1) {
      throw new Refusal(
          "the declaration of "
              + name
              + " declares several variables. Split it into one declaration per variable first.");
    }
    Expression init = fragment.getInitializer();
    if (init == null) {
      throw new Refusal(name + " has no initializer, so there is no value to put in its uses.");
    }
    if (!(stmt.getParent() instanceof Block block)) {
      throw new Refusal(
          "the declaration of " + name + " is not directly in a block. Put it in braces first.");
    }
    int after = stmt.getStartPosition() + stmt.getLength();
    if (Effects.of(block).writes(variable, after)) {
      throw new Refusal(
          name
              + " is assigned after its declaration, so its uses do not all hold the initializer."
              + " Keep the variable, or remove the assignment first.");
    }
    checkShadowed(block, Effects.of(init).named(), after, name, text);
    for (IVariableBinding read : Effects.of(init).named()) {
      if (Effects.of(block).writes(read, after)) {
        throw new Refusal(
            read.getName()
                + " is read by the initializer and assigned after the declaration, so inlining would"
                + " change the value at each use. Keep the variable.");
      }
    }
    if (Effects.of(init).changes()) {
      throw new Refusal(
          "the initializer of "
              + name
              + " calls, assigns or changes a variable, so moving it to each use could change when"
              + " it runs. Keep the variable.");
    }
    if (!variable.getType().isEqualTo(init.resolveTypeBinding())) {
      throw new Refusal(
          "the declared type of "
              + name
              + " is "
              + Extract.typeName(variable.getType())
              + ", not its initializer's, so each use would change type. Keep the variable.");
    }

    checkExceptional(project, block, stmt, init, usesIn(block, variable, after), name, text);
    String source =
        text.substring(init.getStartPosition(), init.getStartPosition() + init.getLength());
    String inlined = needsParentheses(init) ? "(" + source + ")" : source;
    List<TextEdit> edits = new ArrayList<>();
    int uses = 0;
    for (SimpleName use : usesIn(block, variable, after)) {
      edits.add(
          new TextEdit(use.getStartPosition(), use.getStartPosition() + use.getLength(), inlined));
      uses++;
    }
    int declared = lineOf(text, stmt.getStartPosition());
    edits.add(removeLine(text, stmt));
    NameCheck.require(
        project,
        Map.of(file, text),
        Map.of(file, edits),
        NameCheck.meanings(List.of(init)),
        "inlining " + name);
    String updated = TextEdit.apply(text, edits, 0);

    Edits.Written written =
        Edits.gated(
            project,
            file,
            text,
            updated,
            List.of(),
            "inlining " + name,
            "Change the uses in the same way first, or keep the variable.");
    return new Outcome(
        "inlined "
            + name
            + " into "
            + uses
            + (uses == 1 ? " use" : " uses")
            + " and deleted its declaration at line "
            + declared
            + " of "
            + rel
            + written.fixed().said(),
        written.declared());
  }

  /**
   * Reports, or under preserve_exceptions refuses, an initializer that may throw moved past a
   * statement that changes state: the exception would come after that change, not before it.
   */
  private static void checkExceptional(
      SourceProject project,
      Block block,
      Statement declaration,
      Expression init,
      List<SimpleName> uses,
      String name,
      String text) {
    List<ASTNode> throwing = Effects.of(init).mayThrow();
    if (throwing.isEmpty() || uses.isEmpty()) {
      return;
    }
    int first = uses.get(0).getStartPosition();
    for (Object o : block.statements()) {
      Statement s = (Statement) o;
      if (s.getStartPosition() > declaration.getStartPosition()
          && s.getStartPosition() + s.getLength() <= first
          && Effects.of(s).changes()) {
        Exceptional.change(
            project,
            "the initializer of "
                + name
                + " may throw ("
                + throwing.get(0)
                + "), and inlining moves it past the statement at line "
                + Lines.number(text, s.getStartPosition())
                + ", which changes state: an exception would now come after that change",
            "keep the variable, or move that statement above the declaration first");
        return;
      }
    }
  }

  /** The one variable named {@code name} on {@code line}, by column if it is needed. */
  private static IVariableBinding variableAt(
      CompilationUnit unit, String text, String name, int line, Integer column) {
    List<SimpleName> named = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName node) {
            if (node.getIdentifier().equals(name)
                && lineOf(text, node.getStartPosition()) == line) {
              named.add(node);
            }
            return true;
          }
        });
    List<IVariableBinding> found = new ArrayList<>();
    for (SimpleName n : named) {
      if (column != null && n.getStartPosition() - lineStart(text, line) + 1 != column) {
        continue;
      }
      if (n.resolveBinding() instanceof IVariableBinding v) {
        IVariableBinding decl = v.getVariableDeclaration();
        if (found.stream().noneMatch(f -> f.isEqualTo(decl))) {
          found.add(decl);
        }
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          "no variable named "
              + name
              + " on line "
              + line
              + ". Give a line where it is declared or"
              + " used.");
    }
    if (found.size() > 1) {
      throw new Refusal(
          name
              + " means two different variables on line "
              + line
              + "; pass column, the 1-based column of the one you mean.");
    }
    return found.get(0);
  }

  private static VariableDeclarationFragment declarationOf(
      CompilationUnit unit, IVariableBinding variable, String name) {
    VariableDeclarationFragment[] found = {null};
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(VariableDeclarationFragment node) {
            if (node.resolveBinding() != null && node.resolveBinding().isEqualTo(variable)) {
              found[0] = node;
            }
            return true;
          }
        });
    if (found[0] == null) {
      throw new Refusal("the declaration of " + name + " was not found in the file.");
    }
    return found[0];
  }

  /** Uses of {@code variable} in {@code block} after {@code after}, in source order. */
  private static List<SimpleName> usesIn(Block block, IVariableBinding variable, int after) {
    List<SimpleName> uses = new ArrayList<>();
    block.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName node) {
            if (node.getStartPosition() >= after
                && node.resolveBinding() instanceof IVariableBinding v
                && v.getVariableDeclaration().isEqualTo(variable)) {
              uses.add(node);
            }
            return true;
          }
        });
    return uses;
  }

  /** Refuses an initializer that reads a name a later declaration in the block hides at a use. */
  private static void checkShadowed(
      Block block, List<IVariableBinding> reads, int after, String name, String text) {
    for (IVariableBinding read : reads) {
      VariableDeclarationFragment[] later = {null};
      block.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
              if (later[0] == null
                  && node.getStartPosition() >= after
                  && node.getName().getIdentifier().equals(read.getName())) {
                later[0] = node;
              }
              return true;
            }
          });
      if (later[0] != null) {
        throw new Refusal(
            "the initializer of "
                + name
                + " reads "
                + read.getName()
                + ", and the "
                + read.getName()
                + " declared at line "
                + lineOf(text, later[0].getStartPosition())
                + " hides it at a use of "
                + name
                + ", so inlining would read the new one. Rename that variable first.");
      }
    }
  }

  /** Whether {@code e} needs parentheses where it replaces a name. */
  static boolean needsParentheses(Expression e) {
    return !(e instanceof SimpleName
        || e instanceof org.eclipse.jdt.core.dom.QualifiedName
        || e instanceof org.eclipse.jdt.core.dom.NumberLiteral
        || e instanceof org.eclipse.jdt.core.dom.StringLiteral
        || e instanceof org.eclipse.jdt.core.dom.CharacterLiteral
        || e instanceof org.eclipse.jdt.core.dom.BooleanLiteral
        || e instanceof org.eclipse.jdt.core.dom.NullLiteral
        || e instanceof org.eclipse.jdt.core.dom.MethodInvocation
        || e instanceof org.eclipse.jdt.core.dom.FieldAccess
        || e instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression
        || e instanceof org.eclipse.jdt.core.dom.ThisExpression
        || e instanceof org.eclipse.jdt.core.dom.ArrayAccess
        || e instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation);
  }

  /** The edit that deletes the whole line of {@code stmt}, which must be alone on it. */
  private static TextEdit removeLine(String text, ASTNode stmt) {
    int start = stmt.getStartPosition();
    int end = start + stmt.getLength();
    int lineStart = text.lastIndexOf('\n', start - 1) + 1;
    int lineEnd = text.indexOf('\n', end);
    lineEnd = lineEnd < 0 ? text.length() : lineEnd + 1;
    if (!text.substring(lineStart, start).isBlank() || !text.substring(end, lineEnd).isBlank()) {
      throw new Refusal(
          "the declaration shares its line with other code. Put it on a line of its own first.");
    }
    return new TextEdit(lineStart, lineEnd, "");
  }

  private static int lineOf(String text, int offset) {
    return Lines.number(text, offset);
  }

  private static int lineStart(String text, int line) {
    int offset = 0;
    for (int i = 1; i < line; i++) {
      offset = text.indexOf('\n', offset) + 1;
    }
    return offset;
  }
}
