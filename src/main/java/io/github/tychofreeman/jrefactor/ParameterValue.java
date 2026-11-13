package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * An expression of a method's parameters: what {@code extract_variable} with {@code as=parameter}
 * has each call pass, and what {@code remove_parameter} with {@code value} has the method compute.
 */
final class ParameterValue {

  /** The name the probe declares a value under: no source name has it. */
  private static final String PROBE = "value$of$";

  private ParameterValue() {}

  /** The index of {@code v} among {@code m}'s parameters, or -1. */
  static int parameterIndex(MethodDeclaration m, IVariableBinding v) {
    for (int i = 0; i < m.parameters().size(); i++) {
      IVariableBinding p = ((SingleVariableDeclaration) m.parameters().get(i)).resolveBinding();
      if (p != null && p.getKey().equals(v.getVariableDeclaration().getKey())) {
        return i;
      }
    }
    return -1;
  }

  /** Whether {@code m}'s body assigns {@code v} (=, a compound assignment, ++ or --). */
  static boolean assigned(MethodDeclaration m, IVariableBinding v) {
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
   * Whether {@code e} is literals, constants and parameters of its method that the method never
   * assigns, so each call can pass it with its own arguments in place of the parameters.
   */
  static boolean byParameters(Expression e) {
    MethodDeclaration m = Introduce.enclosing(e, MethodDeclaration.class);
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

  /**
   * The argument a call passes for {@code e} (source text {@code source}): {@code e} with each of
   * its method's parameters replaced by that call's argument, parenthesized unless it is a name or
   * a literal; refused for an argument with effects, which would run twice.
   */
  static java.util.function.Function<Signatures.Call, String> perCall(Expression e, String source) {
    List<int[]> uses = occurrences(e, Introduce.enclosing(e, MethodDeclaration.class));
    return c -> {
      for (int[] u : uses) {
        Expression arg = (Expression) c.arguments().get(u[2]);
        if (!quiet(arg)) {
          throw new Refusal(
              "a call passes "
                  + c.file().text().substring(arg.getStartPosition(), Nodes.end(arg))
                  + " for "
                  + source.substring(u[0], u[0] + u[1])
                  + ", which has effects, and passing "
                  + source
                  + " with it would run them twice. Put it in a local first.");
        }
      }
      return substituted(uses, source, c);
    };
  }

  /**
   * Where {@code e} names a parameter of {@code m}: the offset within {@code e}, the length and the
   * parameter's index, the last first.
   */
  private static List<int[]> occurrences(Expression e, MethodDeclaration m) {
    List<int[]> uses = new ArrayList<>();
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding v && parameterIndex(m, v) >= 0) {
        uses.add(
            new int[] {
              n.getStartPosition() - e.getStartPosition(), n.getLength(), parameterIndex(m, v)
            });
      }
    }
    uses.sort((a, b) -> b[0] - a[0]);
    return uses;
  }

  /**
   * {@code source} with each of {@code uses} replaced by the argument {@code c} passes for that
   * parameter, parenthesized unless it is a name or a literal.
   */
  private static String substituted(List<int[]> uses, String source, Signatures.Call c) {
    StringBuilder out = new StringBuilder(source);
    for (int[] u : uses) {
      Expression arg = (Expression) c.arguments().get(u[2]);
      String text = c.file().text().substring(arg.getStartPosition(), Nodes.end(arg));
      boolean plain =
          arg instanceof Name || arg instanceof NumberLiteral || arg instanceof StringLiteral;
      out.replace(u[0], u[0] + u[1], plain ? text : "(" + text + ")");
    }
    return out.toString();
  }

  /**
   * {@code value}, an expression a model wrote for {@code m}'s parameter at {@code at}, read where
   * the body of {@code m} starts: {@code expression} has the bindings its names have there, {@code
   * method} is {@code m} in that parse.
   */
  record InMethod(String text, Expression expression, MethodDeclaration method, int at) {

    /** The indexes of the parameters the value reads. */
    java.util.Set<Integer> uses() {
      java.util.Set<Integer> out = new java.util.TreeSet<>();
      for (SimpleName n : Nodes.all(expression, SimpleName.class)) {
        if (n.resolveBinding() instanceof IVariableBinding v && parameterIndex(method, v) >= 0) {
          out.add(parameterIndex(method, v));
        }
      }
      return out;
    }

    /**
     * Whether nothing a call's other arguments run could change it: no field read but a constant.
     */
    boolean fixed() {
      return Effects.of(expression).fixed();
    }

    /** Whether computing it may throw. */
    boolean mayThrow() {
      return !Effects.of(expression).mayThrow().isEmpty();
    }

    /**
     * The value with {@code c}'s arguments in place of the parameters, as {@code c} would write it.
     */
    String at(Signatures.Call c) {
      return substituted(occurrences(expression, method), text, c);
    }
  }

  /**
   * Reads {@code value} as the first thing the body of {@code d}'s method computes, in place of its
   * parameter at {@code at}: refused unless it parses and each name in it is another parameter, a
   * field or a constant there. {@code label} names the method in the refusal.
   */
  static InMethod inMethod(
      SourceProject project, Signatures.Decl d, int at, String value, String label) {
    MethodDeclaration m = d.m();
    Signatures.parsesAs("value", value, "class C { Object o = " + value + "; }");
    SingleVariableDeclaration p = (SingleVariableDeclaration) m.parameters().get(at);
    String text = d.file().text();
    String type = text.substring(p.getType().getStartPosition(), Nodes.end(p.getType()));
    int where = afterConstructorCall(m);
    String before = " " + type + " " + PROBE + " = ";
    CompilationUnit unit =
        Jdt.parse(
            project,
            d.file().file(),
            text.substring(0, where) + before + value + ";" + text.substring(where));
    ASTNode found = org.eclipse.jdt.core.dom.NodeFinder.perform(unit, where + before.length(), 0);
    while (found != null && !(found instanceof VariableDeclarationFragment)) {
      found = found.getParent();
    }
    if (!(found instanceof VariableDeclarationFragment f)
        || f.getInitializer() == null
        || f.getInitializer().getStartPosition() != where + before.length()
        || f.getInitializer().getLength() != value.length()
        || !(Introduce.enclosing(f, MethodDeclaration.class) instanceof MethodDeclaration probe)) {
      throw new Refusal("value " + value + " does not parse as an expression.");
    }
    boolean isStatic = Modifier.isStatic(m.getModifiers());
    for (SimpleName n : Nodes.all(f.getInitializer(), SimpleName.class)) {
      IBinding b = n.resolveBinding();
      boolean known =
          switch (b) {
            case null -> false;
            case IVariableBinding v when parameterIndex(probe, v) >= 0 ->
                parameterIndex(probe, v) != at;
            case IVariableBinding v when v.isField() ->
                !bare(n) || !isStatic || Modifier.isStatic(v.getModifiers());
            case IVariableBinding v -> false;
            default -> true;
          };
      if (!known) {
        List<String> others = new ArrayList<>();
        for (int i = 0; i < m.parameters().size(); i++) {
          if (i != at) {
            others.add(((SingleVariableDeclaration) m.parameters().get(i)).getName().toString());
          }
        }
        throw new Refusal(
            "value may use "
                + label.substring(0, label.indexOf('('))
                + "'s other parameters"
                + (others.isEmpty() ? "" : " (" + String.join(", ", others) + ")")
                + ", fields, constants and literals; "
                + n.getIdentifier()
                + " is none of these there.");
      }
    }
    return new InMethod(value, f.getInitializer(), probe, at);
  }

  /** Whether {@code n} is a name on its own: not the member that a qualifier or a receiver has. */
  private static boolean bare(SimpleName n) {
    return switch (n.getParent()) {
      case QualifiedName q -> q.getName() != n;
      case FieldAccess f -> f.getName() != n || f.getExpression() instanceof ThisExpression;
      default -> true;
    };
  }

  /**
   * Where the first statement of {@code m}'s body goes: just after its {@code this(...)} or {@code
   * super(...)} when it starts with one, else just after its opening brace.
   */
  static int afterConstructorCall(MethodDeclaration m) {
    List<?> statements = m.getBody().statements();
    if (!statements.isEmpty()
        && (statements.get(0) instanceof ConstructorInvocation
            || statements.get(0) instanceof SuperConstructorInvocation)) {
      return Nodes.end((ASTNode) statements.get(0));
    }
    return m.getBody().getStartPosition() + 1;
  }

  /** Whether evaluating {@code e} twice, or not at all, shows nowhere: it only reads. */
  static boolean quiet(Expression e) {
    for (Effects.Effect x : Effects.of(e).all()) {
      if (x.deferred()
          || (x.kind() != Effects.Kind.NAME
              && x.kind() != Effects.Kind.ELEMENT
              && x.kind() != Effects.Kind.MAY_THROW)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether {@code argument}, which the call {@code c} passes, is {@code value} with that call's
   * arguments in place of the parameters: the same structure, each other name with the same meaning
   * (by binding key; the two come from different parses), a field of the method's object only on a
   * call on {@code this}.
   */
  static boolean passes(InMethod value, Signatures.Call c, Expression argument) {
    return new Instance(value.method(), c).safeSubtreeMatch(value.expression(), argument);
  }

  private static Object unwrapped(Object o) {
    while (o instanceof ParenthesizedExpression p) {
      o = p.getExpression();
    }
    return o;
  }

  /** The key that names what {@code b} declares in every parse, or null. */
  private static String key(IBinding b) {
    return switch (b) {
      case null -> null;
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      case IMethodBinding m -> m.getMethodDeclaration().getKey();
      case ITypeBinding t -> t.getErasure().getKey();
      default -> b.getKey();
    };
  }

  /** The matcher of {@link #passes}: the pattern is the value, the other side the argument. */
  private static final class Instance extends ASTMatcher {
    final MethodDeclaration method;
    final Signatures.Call call;

    Instance(MethodDeclaration method, Signatures.Call call) {
      super(false);
      this.method = method;
      this.call = call;
    }

    @Override
    public boolean match(SimpleName p, Object other) {
      other = unwrapped(other);
      IBinding b = p.resolveBinding();
      if (b instanceof IVariableBinding v) {
        int i = parameterIndex(method, v);
        if (i >= 0) {
          return other instanceof Expression x
              && ((ASTNode) unwrapped(call.arguments().get(i))).subtreeMatch(new ASTMatcher(), x);
        }
        return v.isField() && field(v, other);
      }
      return other instanceof SimpleName o
          && o.getIdentifier().equals(p.getIdentifier())
          && key(b) != null
          && key(b).equals(key(o.resolveBinding()));
    }

    @Override
    public boolean match(QualifiedName p, Object other) {
      other = unwrapped(other);
      if (!(p.resolveBinding() instanceof IVariableBinding v) || !v.isField()) {
        return false;
      }
      if (Modifier.isStatic(v.getModifiers())) {
        return field(v, other);
      }
      return member(v, p.getQualifier(), other);
    }

    @Override
    public boolean match(FieldAccess p, Object other) {
      other = unwrapped(other);
      IVariableBinding v = p.resolveFieldBinding();
      if (v == null) {
        return false;
      }
      if (p.getExpression() instanceof ThisExpression t && t.getQualifier() == null) {
        return field(v, other);
      }
      return member(v, p.getExpression(), other);
    }

    @Override
    public boolean match(ParenthesizedExpression p, Object other) {
      return safeSubtreeMatch(p.getExpression(), unwrapped(other));
    }

    @Override
    public boolean match(InfixExpression p, Object other) {
      return super.match(p, unwrapped(other));
    }

    /** Whether {@code other} reads the field {@code v} of what {@code of} matches. */
    private boolean member(IVariableBinding v, Expression of, Object other) {
      return switch (other) {
        case QualifiedName o ->
            same(v, o.resolveBinding()) && safeSubtreeMatch(of, o.getQualifier());
        case FieldAccess o ->
            same(v, o.resolveFieldBinding()) && safeSubtreeMatch(of, o.getExpression());
        default -> false;
      };
    }

    /**
     * Whether {@code other} reads the field {@code v}, of the method's own object when it is an
     * instance's: unqualified or through this, on a call on this.
     */
    private boolean field(IVariableBinding v, Object other) {
      IBinding b =
          switch (other) {
            case Name n -> n.resolveBinding();
            case FieldAccess f -> f.resolveFieldBinding();
            default -> null;
          };
      if (!same(v, b)) {
        return false;
      }
      if (Modifier.isStatic(v.getModifiers())) {
        return other instanceof Name;
      }
      boolean ownArgument =
          other instanceof SimpleName
              || other instanceof FieldAccess f
                  && f.getExpression() instanceof ThisExpression t
                  && t.getQualifier() == null;
      boolean ownCall =
          call.node() instanceof MethodInvocation mi
              && (mi.getExpression() == null
                  || mi.getExpression() instanceof ThisExpression t && t.getQualifier() == null);
      return ownArgument && ownCall;
    }

    /** Whether {@code b} is the variable {@code v}; an array's length has no key, so by name. */
    private static boolean same(IVariableBinding v, IBinding b) {
      if (!(b instanceof IVariableBinding o)) {
        return false;
      }
      return key(v) == null
          ? key(o) == null && v.getName().equals(o.getName())
          : key(v).equals(key(o));
    }
  }
}
