package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * extract_variable with as: the expression becomes a constant ({@code private static final}), a
 * field ({@code private final}) of the enclosing type, or a new parameter of the enclosing method
 * that every call passes it to. It must be literals and constants only: anything else would be
 * evaluated at another time (a field's initializer runs once) or place (a call site).
 */
final class Introduce {

  private Introduce() {}

  static Outcome run(
      SourceProject project,
      Opened opened,
      Expression e,
      String name,
      String as,
      ITypeBinding type,
      String source,
      int line)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> List.of("constant", "field", "parameter").contains(as),
            () -> "as is constant, field or parameter: what " + source + " becomes."),
        Precondition.that(
            () -> as.equals("parameter") ? byParameters(e) : constantOnly(e),
            () ->
                source
                    + (as.equals("parameter")
                        ? " uses a local, a field that is not a constant, a call, or a parameter"
                            + " the method assigns, so a call could not pass it"
                        : " uses a local, a parameter, a field that is not a constant, or a call,"
                            + " so as a "
                            + as
                            + " it would be evaluated at another time or place")
                    + ". Extract it as a variable instead."));
    String typeName = Extract.typeName(type);
    String rel = opened.rel();
    if (as.equals("parameter")) {
      return parameter(project, opened, e, name, typeName, source, line);
    }
    AbstractTypeDeclaration owner = enclosing(e, AbstractTypeDeclaration.class);
    BodyDeclaration member = enclosing(e, BodyDeclaration.class);
    Precondition.require(
        Precondition.that(
            () -> owner != null && owner.resolveBinding() != null,
            () -> source + " is not in a type that resolves."),
        Precondition.that(
            () ->
                as.equals("constant")
                    || member == null
                    || !Modifier.isStatic(member.getModifiers()),
            () ->
                source
                    + " is in a static member, which an instance field cannot serve. Use"
                    + " as=constant."));
    for (IVariableBinding f : owner.resolveBinding().getDeclaredFields()) {
      if (f.getName().equals(name)) {
        throw new Refusal(
            owner.getName().getIdentifier()
                + " already has a field "
                + name
                + ". Choose another name.");
      }
    }
    String decl =
        "private "
            + (as.equals("constant") ? "static final " : "final ")
            + typeName
            + " "
            + name
            + " = "
            + source
            + ";";
    String text = opened.text();
    List<TextEdit> edits = new ArrayList<>(MemberMove.addTo(text, owner, decl, 0));
    edits.add(new TextEdit(e.getStartPosition(), e.getStartPosition() + e.getLength(), name));
    String after = TextEdit.apply(text, edits, 0);
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            after,
            List.of(),
            "introducing " + as + " " + name,
            "Choose a name nothing else uses.");
    return new Outcome(
        "introduced "
            + as
            + " "
            + name
            + " ("
            + decl
            + ") in "
            + owner.getName().getIdentifier()
            + ", in place of "
            + source
            + " at line "
            + line
            + " of "
            + rel
            + written.fixed().said(),
        written.declared());
  }

  /**
   * As a parameter: {@code add_parameter} adds it to the enclosing method (every call passes the
   * expression), then the expression is replaced with its name.
   */
  private static Outcome parameter(
      SourceProject project,
      Opened opened,
      Expression e,
      String name,
      String typeName,
      String source,
      int line)
      throws Exception {
    MethodDeclaration m = enclosing(e, MethodDeclaration.class);
    Precondition.require(
        Precondition.that(
            () -> m != null && m.resolveBinding() != null,
            () -> source + " is not directly in a method, whose parameter it could be."));
    int offset = e.getStartPosition() - Lines.start(opened.text(), e.getStartPosition());
    Outcome added =
        Signatures.addParameter(
            project,
            new Args(
                "add_parameter",
                Map.of(
                    "file", opened.rel(),
                    "name", Signatures.label(m.resolveBinding()),
                    "parameter", name,
                    "type", typeName,
                    "value", source)),
            perCall(e, source));
    // The signature changed; the expression is found again on its line, by its text.
    Opened again = Opened.of(project, opened.rel());
    String text = again.text();
    int lineStart = Lines.start(text, again.unit().getPosition(line, 0));
    int from = text.indexOf(source, lineStart + Math.max(0, offset - 1));
    if (from < 0 || Lines.number(text, from) != line) {
      from = text.indexOf(source, lineStart);
    }
    String after = text.substring(0, from) + name + text.substring(from + source.length());
    Edits.Written written =
        Edits.gated(
            project,
            again.file(),
            text,
            after,
            List.of(),
            "using parameter " + name,
            "Nothing was changed.");
    return new Outcome(
        added.text() + "; " + source + " at line " + line + " is now " + name, written.declared());
  }

  /** Whether {@code e} is literals, constants and operators on them. */
  private static boolean constantOnly(Expression e) {
    for (Effects.Effect x : Effects.of(e).all()) {
      switch (x.kind()) {
        case NAME -> {
          IVariableBinding v = x.variable();
          boolean constant =
              v.isField()
                  && Modifier.isStatic(v.getModifiers())
                  && Modifier.isFinal(v.getModifiers());
          if (!constant) {
            return false;
          }
        }
        case MAY_THROW -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Whether {@code e} is literals, constants and parameters of its method that the method never
   * assigns, so each call can pass it with its own arguments in place of the parameters.
   */
  private static boolean byParameters(Expression e) {
    MethodDeclaration m = enclosing(e, MethodDeclaration.class);
    if (m == null) {
      return false;
    }
    for (Effects.Effect x : Effects.of(e).all()) {
      switch (x.kind()) {
        case NAME -> {
          IVariableBinding v = x.variable();
          boolean constant =
              v.isField()
                  && Modifier.isStatic(v.getModifiers())
                  && Modifier.isFinal(v.getModifiers());
          if (!constant && parameterIndex(m, v) < 0) {
            return false;
          }
          if (!constant && assigned(m, v)) {
            return false;
          }
        }
        case MAY_THROW -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }

  /** The index of {@code v} among {@code m}'s parameters, or -1. */
  private static int parameterIndex(MethodDeclaration m, IVariableBinding v) {
    for (int i = 0; i < m.parameters().size(); i++) {
      IVariableBinding p = ((SingleVariableDeclaration) m.parameters().get(i)).resolveBinding();
      if (p != null && p.getKey().equals(v.getVariableDeclaration().getKey())) {
        return i;
      }
    }
    return -1;
  }

  /** Whether {@code m}'s body assigns {@code v} (=, a compound assignment, ++ or --). */
  private static boolean assigned(MethodDeclaration m, IVariableBinding v) {
    String key = v.getVariableDeclaration().getKey();
    for (SimpleName n : Nodes.all(m, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding b
          && b.getVariableDeclaration().getKey().equals(key)) {
        ASTNode p = n.getParent();
        if ((p instanceof Assignment a && a.getLeftHandSide() == n)
            || p instanceof PostfixExpression
            || (p instanceof PrefixExpression pre
                && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                    || pre.getOperator() == PrefixExpression.Operator.DECREMENT))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The argument a call passes for {@code e} (source text {@code source}): {@code e} with each of
   * its method's parameters replaced by that call's argument, parenthesized unless it is a name or
   * a literal; refused for an argument with effects, which would run twice.
   */
  private static java.util.function.Function<Signatures.Call, String> perCall(
      Expression e, String source) {
    MethodDeclaration m = enclosing(e, MethodDeclaration.class);
    java.util.List<int[]> uses = new java.util.ArrayList<>();
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding v && parameterIndex(m, v) >= 0) {
        uses.add(
            new int[] {
              n.getStartPosition() - e.getStartPosition(), n.getLength(), parameterIndex(m, v)
            });
      }
    }
    uses.sort((a, b) -> b[0] - a[0]);
    return c -> {
      StringBuilder out = new StringBuilder(source);
      for (int[] u : uses) {
        Expression arg = (Expression) c.arguments().get(u[2]);
        String text = c.file().text().substring(arg.getStartPosition(), Nodes.end(arg));
        for (Effects.Effect x : Effects.of(arg).all()) {
          if (x.kind() != Effects.Kind.NAME && x.kind() != Effects.Kind.MAY_THROW) {
            throw new Refusal(
                "a call passes "
                    + text
                    + " for "
                    + source.substring(u[0], u[0] + u[1])
                    + ", which has effects, and passing "
                    + source
                    + " with it would run them twice. Put it in a local first.");
          }
        }
        boolean plain =
            arg instanceof Name || arg instanceof NumberLiteral || arg instanceof StringLiteral;
        out.replace(u[0], u[0] + u[1], plain ? text : "(" + text + ")");
      }
      return out.toString();
    };
  }

  /** The nearest {@code type} around {@code n}, or null past a lambda or an anonymous class. */
  private static <T extends ASTNode> T enclosing(ASTNode n, Class<T> type) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (type.isInstance(p)) {
        return type.cast(p);
      }
      if (p instanceof LambdaExpression || p instanceof AnonymousClassDeclaration) {
        return null;
      }
      if (p instanceof CompilationUnit) {
        return null;
      }
    }
    return null;
  }
}
