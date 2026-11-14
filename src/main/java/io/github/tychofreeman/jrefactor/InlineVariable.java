package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
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
    IVariableBinding variable = variableAt(opened, name, line, column);
    if (variable.isField()) {
      throw new Refusal(
          name
              + " is a field. inline with line inlines a local variable; a field needs its uses"
              + " changed another way.");
    }
    if (variable.isParameter()) {
      throw new Refusal(
          name
              + " is a parameter. inline with line inlines a local variable declared with an"
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
    if (!variable.getType().isEqualTo(init.resolveTypeBinding())) {
      throw new Refusal(
          "the declared type of "
              + name
              + " is "
              + Extract.typeName(variable.getType())
              + ", not its initializer's, so each use would change type. Keep the variable.");
    }

    String source =
        text.substring(init.getStartPosition(), init.getStartPosition() + init.getLength());
    boolean array = init instanceof ArrayInitializer;
    if (array) {
      // An initializer alone is an expression only in a declaration; elsewhere new and the type.
      Type type = stmt.getType();
      source =
          "new "
              + text.substring(type.getStartPosition(), Nodes.end(type))
              + "[]".repeat(fragment.getExtraDimensions())
              + " "
              + source;
    }
    String inlined = needsParentheses(init) && !array ? "(" + source + ")" : source;
    List<TextEdit> edits = new ArrayList<>();
    int uses = 0;
    for (SimpleName use : usesIn(block, variable, after)) {
      // new T[] {...}[i] does not parse: an array creation is indexed in parentheses.
      boolean indexed = use.getParent() instanceof ArrayAccess a && a.getArray() == use;
      edits.add(
          new TextEdit(
              use.getStartPosition(),
              use.getStartPosition() + use.getLength(),
              array && indexed ? "(" + source + ")" : inlined));
      uses++;
    }
    int declared = Lines.number(text, stmt.getStartPosition());
    edits.add(removeLine(text, stmt));
    Behavior.check(
        project,
        new Behavior.Move(
            init,
            block,
            usesIn(block, variable, after),
            "the initializer of " + name,
            "inlining " + name),
        Map.of(file, text),
        Map.of(file, edits));
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

  /** The one variable named {@code name} on {@code line}, by column if it is needed. */
  private static IVariableBinding variableAt(Opened opened, String name, int line, Integer column) {
    List<SimpleName> named = new ArrayList<>();
    for (SimpleName node : opened.onLine(line, SimpleName.class)) {
      if (node.getIdentifier().equals(name)) {
        named.add(node);
      }
    }
    List<IVariableBinding> found = new ArrayList<>();
    List<SimpleName> first = new ArrayList<>();
    for (SimpleName n : named.isEmpty() ? named : opened.atColumn(named, line, column, name)) {
      if (n.resolveBinding() instanceof IVariableBinding v) {
        IVariableBinding decl = v.getVariableDeclaration();
        if (found.stream().noneMatch(f -> f.isEqualTo(decl))) {
          found.add(decl);
          first.add(n);
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
      throw opened.several(first, line, "variables named " + name);
    }
    return found.get(0);
  }

  private static VariableDeclarationFragment declarationOf(
      CompilationUnit unit, IVariableBinding variable, String name) {
    VariableDeclarationFragment[] found = {null};
    for (VariableDeclarationFragment node : Nodes.all(unit, VariableDeclarationFragment.class)) {
      if (node.resolveBinding() != null && node.resolveBinding().isEqualTo(variable)) {
        found[0] = node;
      }
    }
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
                + Lines.number(text, later[0].getStartPosition())
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
}
