package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
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
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
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
   * Whether {@code e} is literals, constants, parameters of its method that the method never
   * assigns, and calls on them, on a class or on the method's own object, so each call can pass it
   * with its own arguments in place of the parameters.
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
        case CALL -> {
          if (x.deferred() || !(x.node() instanceof MethodInvocation call) || !reachable(call, m)) {
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
   * The argument a call passes for {@code v}, the value of a new parameter: {@code v} with each of
   * its method's parameters replaced by that call's argument, parenthesized unless it is a name or
   * a literal. Refused for an argument that would then run twice, be read in another type, or be
   * read again after something that could change it.
   */
  static java.util.function.Function<Signatures.Call, String> perCall(
      SourceProject project, InMethod v) {
    return c -> {
      for (int i : v.uses()) {
        Expression arg = (Expression) c.arguments().get(i);
        String passed = c.file().text().substring(arg.getStartPosition(), Nodes.end(arg));
        String name =
            ((SingleVariableDeclaration) v.method().parameters().get(i)).getName().getIdentifier();
        if (!quiet(arg)) {
          throw new Refusal(
              "a call passes "
                  + passed
                  + " for "
                  + name
                  + ", which has effects, and passing "
                  + v.text()
                  + " with it would run them twice. Put it in a local first.");
        }
        ITypeBinding declared =
            ((SingleVariableDeclaration) v.method().parameters().get(i)).getType().resolveBinding();
        ITypeBinding given = arg.resolveTypeBinding();
        if (declared != null
            && (given == null || declared.isPrimitive() || given.isPrimitive())
            && (given == null || !declared.getName().equals(given.getName()))) {
          throw new Refusal(
              "the call at "
                  + c.where(arg)
                  + " passes "
                  + passed
                  + (given == null ? "" : " (" + given.getName() + ")")
                  + " for "
                  + name
                  + " ("
                  + declared.getName()
                  + "): the method computed "
                  + v.text()
                  + " in the parameter's type, and the call would compute it in the argument's. Pass "
                  + name
                  + " in its own type first, or extract it as a variable instead.");
        }
        boolean fixed = Effects.of(arg).fixed();
        if (v.acts() && !fixed && !v.readsFirst(i)) {
          throw new Refusal(
              "the call at "
                  + c.where(arg)
                  + " passes "
                  + passed
                  + " for "
                  + name
                  + ", and would now pass "
                  + v.at(c)
                  + ", reading "
                  + passed
                  + " again after a call in it that could change it. Put "
                  + passed
                  + " in a local first.");
        }
        for (int j = i + 1; j < c.arguments().size(); j++) {
          Expression other = (Expression) c.arguments().get(j);
          if (!quiet(other) && (!fixed || writes(other, arg))) {
            throw new Refusal(
                "the call at "
                    + c.where(other)
                    + " runs "
                    + other
                    + " after "
                    + passed
                    + ", and would now pass "
                    + v.at(c)
                    + " last, reading "
                    + passed
                    + " again after "
                    + other
                    + " could change it. Put "
                    + other
                    + " in a local first.");
          }
        }
      }
      sameTargets(project, v, c);
      return v.at(c);
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
   * parameter, parenthesized unless it is a name or a literal, and each of {@code more} written.
   */
  private static String substituted(
      List<int[]> uses, String source, Signatures.Call c, List<Swap> more) {
    List<Swap> swaps = new ArrayList<>(more);
    for (int[] u : uses) {
      Expression arg = (Expression) c.arguments().get(u[2]);
      String text = c.file().text().substring(arg.getStartPosition(), Nodes.end(arg));
      boolean plain =
          arg instanceof Name || arg instanceof NumberLiteral || arg instanceof StringLiteral;
      swaps.add(new Swap(u[0], u[1], plain ? text : "(" + text + ")"));
    }
    swaps.sort((a, b) -> b.offset() - a.offset());
    StringBuilder out = new StringBuilder(source);
    for (Swap s : swaps) {
      out.replace(s.offset(), s.offset() + s.length(), s.text());
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

    /** Whether computing it does something: a call, a new object. */
    boolean acts() {
      return Effects.of(expression).changes();
    }

    /**
     * Whether each read of the parameter at {@code index} comes before everything the value does:
     * it is in the call's own receiver or arguments, or written before it.
     */
    boolean readsFirst(int index) {
      for (SimpleName n : Nodes.all(expression, SimpleName.class)) {
        if (!(n.resolveBinding() instanceof IVariableBinding v)
            || parameterIndex(method, v) != index) {
          continue;
        }
        for (Effects.Effect x : Effects.of(expression).all()) {
          if (x.changes()
              && !Nodes.within(n, x.node())
              && n.getStartPosition() > x.node().getStartPosition()) {
            return false;
          }
        }
      }
      return true;
    }

    /** Whether computing it may throw. */
    boolean mayThrow() {
      return !Effects.of(expression).mayThrow().isEmpty();
    }

    /**
     * The value with {@code c}'s arguments in place of the parameters, as {@code c} would write it.
     */
    String at(Signatures.Call c) {
      return written(c, false);
    }

    /**
     * The value as {@code c} passes it: its arguments in place of the parameters, {@code c}'s
     * receiver before each call on the method's own object, and a static method's class before it
     * where its name alone does not reach it; with {@code probe}, by the class's qualified name,
     * which needs no import.
     */
    String written(Signatures.Call c, boolean probe) {
      List<Swap> swaps = new ArrayList<>();
      for (MethodInvocation call : Nodes.all(expression, MethodInvocation.class)) {
        IMethodBinding b = call.resolveMethodBinding();
        Expression on = call.getExpression();
        if (b == null || on != null && !(on instanceof ThisExpression)) {
          continue;
        }
        String before;
        if (Modifier.isStatic(b.getModifiers())) {
          ITypeBinding type = b.getDeclaringClass();
          before =
              reaches(c.node(), type)
                  ? null
                  : probe ? type.getErasure().getQualifiedName() : Extract.typeName(type);
        } else {
          before =
              switch (c.node()) {
                case SuperMethodInvocation s -> "super";
                case MethodInvocation mi
                    when mi.getExpression() != null
                        && !(mi.getExpression() instanceof ThisExpression t
                            && t.getQualifier() == null) -> {
                  Expression r = mi.getExpression();
                  String text = c.file().text().substring(r.getStartPosition(), Nodes.end(r));
                  yield r instanceof Name || r instanceof ThisExpression || r instanceof FieldAccess
                      ? text
                      : "(" + text + ")";
                }
                default -> null;
              };
        }
        if (before != null) {
          swaps.add(
              new Swap(
                  call.getStartPosition() - expression.getStartPosition(),
                  call.getName().getStartPosition() - call.getStartPosition(),
                  before + "."));
        }
      }
      return substituted(occurrences(expression, method), text, c, swaps);
    }

    /**
     * The value with {@code c}'s arguments in place of the parameters, each call written as in the
     * method.
     */
    String inPlace(Signatures.Call c) {
      return substituted(occurrences(expression, method), text, c, List.of());
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

    @Override
    public boolean match(ThisExpression p, Object other) {
      return unwrapped(other) instanceof ThisExpression o
          && p.getQualifier() == null
          && o.getQualifier() == null
          && ownCall();
    }

    @Override
    public boolean match(SuperMethodInvocation p, Object other) {
      return false;
    }

    /**
     * A call matches a call of the same method, or of one that overrides it when it is called on an
     * object, with matching arguments; a method of the method's own object only on a call on this.
     */
    @Override
    public boolean match(MethodInvocation p, Object other) {
      if (!(unwrapped(other) instanceof MethodInvocation o)
          || !p.typeArguments().isEmpty()
          || !o.typeArguments().isEmpty()
          || p.arguments().size() != o.arguments().size()) {
        return false;
      }
      IMethodBinding mine = p.resolveMethodBinding();
      IMethodBinding theirs = o.resolveMethodBinding();
      if (mine == null || theirs == null) {
        return false;
      }
      boolean isStatic = Modifier.isStatic(mine.getModifiers());
      boolean own =
          !isStatic && (p.getExpression() == null || p.getExpression() instanceof ThisExpression);
      boolean same = key(mine).equals(key(theirs));
      if (!same && (isStatic || own || !overrides(theirs, mine))) {
        return false;
      }
      if (isStatic) {
        if (!typeOrNone(p.getExpression()) || !typeOrNone(o.getExpression())) {
          return false;
        }
      } else if (own) {
        if (!(o.getExpression() == null
                || o.getExpression() instanceof ThisExpression t && t.getQualifier() == null)
            || !(p.getExpression() == null
                || p.getExpression() instanceof ThisExpression t && t.getQualifier() == null)
            || !ownCall()) {
          return false;
        }
      } else if (o.getExpression() == null
          || !safeSubtreeMatch(p.getExpression(), o.getExpression())) {
        return false;
      }
      for (int i = 0; i < p.arguments().size(); i++) {
        if (!safeSubtreeMatch(p.arguments().get(i), o.arguments().get(i))) {
          return false;
        }
      }
      return true;
    }

    private static boolean typeOrNone(Expression receiver) {
      return receiver == null
          || receiver instanceof Name n && n.resolveBinding() instanceof ITypeBinding;
    }

    /**
     * Whether {@code theirs} overrides {@code mine}: the same name and parameter types, in a
     * subtype of its class. The two are of different parses, so by key.
     */
    private static boolean overrides(IMethodBinding theirs, IMethodBinding mine) {
      ITypeBinding[] a = theirs.getMethodDeclaration().getParameterTypes();
      ITypeBinding[] b = mine.getMethodDeclaration().getParameterTypes();
      if (!theirs.getName().equals(mine.getName())
          || a.length != b.length
          || Modifier.isStatic(theirs.getModifiers())
          || Modifier.isPrivate(mine.getModifiers())) {
        return false;
      }
      for (int i = 0; i < a.length; i++) {
        if (!a[i].getErasure().getKey().equals(b[i].getErasure().getKey())) {
          return false;
        }
      }
      return subtype(theirs.getDeclaringClass(), mine.getDeclaringClass());
    }

    /** Whether the call is on the caller's own object: unqualified, or on this. */
    private boolean ownCall() {
      return call.node() instanceof MethodInvocation mi
          && (mi.getExpression() == null
              || mi.getExpression() instanceof ThisExpression t && t.getQualifier() == null);
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

  /** Whether {@code other} assigns a variable that {@code read} reads. */
  private static boolean writes(Expression other, Expression read) {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (IVariableBinding r : Effects.of(read).named()) {
      names.add(r.getVariableDeclaration().getKey());
    }
    return Effects.of(other).written().stream()
        .anyMatch(w -> names.contains(w.getVariableDeclaration().getKey()));
  }

  /**
   * The expressions in {@code m}'s body that are {@code e}: the same structure, each name with the
   * same binding. {@code e} is one of them.
   */
  static List<Expression> sameIn(MethodDeclaration m, Expression e) {
    ASTMatcher same =
        new ASTMatcher() {
          @Override
          public boolean match(SimpleName p, Object other) {
            return other instanceof SimpleName o
                && o.getIdentifier().equals(p.getIdentifier())
                && key(p.resolveBinding()) != null
                && key(p.resolveBinding()).equals(key(o.resolveBinding()));
          }
        };
    List<Expression> out = new ArrayList<>();
    for (Expression x : Nodes.all(m.getBody(), Expression.class)) {
      if (x.getNodeType() == e.getNodeType() && e.subtreeMatch(same, x)) {
        out.add(x);
      }
    }
    return out;
  }

  /** Text written in place of {@code length} characters at {@code offset} within a value. */
  private record Swap(int offset, int length, String text) {}

  /**
   * Whether each call of {@code m} can write {@code call}, a call in its body: on a receiver it
   * names, a static method's, or one on the method's own object, with no receiver or on this.
   */
  private static boolean reachable(MethodInvocation call, MethodDeclaration m) {
    IMethodBinding b = call.resolveMethodBinding();
    if (b == null) {
      return false;
    }
    boolean onThis = call.getExpression() instanceof ThisExpression t && t.getQualifier() == null;
    if (call.getExpression() != null && !onThis) {
      return !(call.getExpression() instanceof ThisExpression);
    }
    if (Modifier.isStatic(b.getModifiers())) {
      return !onThis;
    }
    IMethodBinding in = m.resolveBinding();
    return in != null && subtype(in.getDeclaringClass(), b.getDeclaringClass());
  }

  /** Whether {@code t} is {@code of} or a subtype of it, by key: the two may be of two parses. */
  static boolean subtype(ITypeBinding t, ITypeBinding of) {
    String target = of.getErasure().getKey();
    java.util.Deque<ITypeBinding> todo = new java.util.ArrayDeque<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    todo.add(t);
    while (!todo.isEmpty()) {
      ITypeBinding x = todo.pop();
      if (!seen.add(x.getErasure().getKey())) {
        continue;
      }
      if (x.getErasure().getKey().equals(target)) {
        return true;
      }
      if (x.getSuperclass() != null) {
        todo.add(x.getSuperclass());
      }
      todo.addAll(List.of(x.getInterfaces()));
    }
    return false;
  }

  /** The types around {@code n}, the nearest first: whose {@code this} code there reads first. */
  private static List<ITypeBinding> typesAround(ASTNode n) {
    List<ITypeBinding> out = new ArrayList<>();
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      ITypeBinding t =
          switch (p) {
            case AbstractTypeDeclaration d -> d.resolveBinding();
            case AnonymousClassDeclaration a -> a.resolveBinding();
            default -> null;
          };
      if (t != null) {
        out.add(t);
      }
    }
    return out;
  }

  /**
   * Whether {@code type}'s static methods are named alone at {@code n}: it is in that type, a
   * subtype, or a type nested in one.
   */
  private static boolean reaches(ASTNode n, ITypeBinding type) {
    return typesAround(n).stream().anyMatch(t -> subtype(t, type));
  }

  /**
   * Whether {@code e}, evaluated again after a call's arguments, is the same object: this, a class,
   * a local or parameter (which no argument may assign), or a final field of one.
   */
  private static boolean steady(Expression e) {
    return switch (e) {
      case ThisExpression t -> true;
      case ParenthesizedExpression p -> steady(p.getExpression());
      case Name n when n.resolveBinding() instanceof ITypeBinding -> true;
      case SimpleName n ->
          n.resolveBinding() instanceof IVariableBinding v
              && (!v.isField() || Modifier.isFinal(v.getModifiers()));
      case QualifiedName q ->
          q.resolveBinding() instanceof IVariableBinding v
              && Modifier.isFinal(v.getModifiers())
              && steady(q.getQualifier());
      case FieldAccess f ->
          f.resolveFieldBinding() != null
              && Modifier.isFinal(f.resolveFieldBinding().getModifiers())
              && steady(f.getExpression());
      default -> false;
    };
  }

  /**
   * Whether {@code site}, or a type of the project below it, declares a method that overrides
   * {@code b}.
   */
  private static boolean overriddenBelow(
      SourceProject project, IMethodBinding b, ITypeBinding site) {
    try {
      List<Path> files = new ArrayList<>();
      for (Path f : project.javaFiles()) {
        if (Files.readString(f).contains(b.getName())) {
          files.add(f);
        }
      }
      for (CompilationUnit u : Jdt.parseAll(project, files).values()) {
        for (MethodDeclaration d : Nodes.all(u, MethodDeclaration.class)) {
          IMethodBinding o = d.resolveBinding();
          if (o != null
              && !key(o).equals(key(b))
              && Instance.overrides(o, b)
              && subtype(o.getDeclaringClass(), site)) {
            return true;
          }
        }
      }
      return false;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Refuses when the value's calls would not run the same code passed at {@code c}: a call on the
   * method's own object where {@code c}'s receiver could be another object when read again, or is
   * an enclosing object, or where {@code super} would skip an override; or a call that resolves
   * there to another method, or to one it cannot see.
   */
  private static void sameTargets(SourceProject project, InMethod v, Signatures.Call c) {
    List<MethodInvocation> calls = Nodes.all(v.expression(), MethodInvocation.class);
    if (calls.isEmpty()) {
      return;
    }
    String method = v.method().getName().getIdentifier();
    ITypeBinding owner = v.method().resolveBinding().getDeclaringClass();
    for (MethodInvocation call : calls) {
      IMethodBinding b = call.resolveMethodBinding();
      if (b == null
          || Modifier.isStatic(b.getModifiers())
          || call.getExpression() != null && !(call.getExpression() instanceof ThisExpression)) {
        continue;
      }
      switch (c.node()) {
        case SuperMethodInvocation s -> {
          List<ITypeBinding> around = typesAround(s);
          if (s.getQualifier() != null
              || around.isEmpty()
              || overriddenBelow(project, b, around.get(0))) {
            throw new Refusal(
                "the call at "
                    + c.where(s)
                    + " is super."
                    + method
                    + "(...), and would pass "
                    + v.at(c)
                    + ": super."
                    + b.getName()
                    + " runs the "
                    + b.getName()
                    + " above "
                    + (around.isEmpty() ? "it" : around.get(0).getName())
                    + ", where "
                    + method
                    + " ran the object's own "
                    + b.getName()
                    + ", which "
                    + (around.isEmpty() ? "a subclass" : around.get(0).getName() + " or a subclass")
                    + " overrides. Write that call as "
                    + method
                    + "(...) first (no subclass overrides "
                    + method
                    + ", so it runs the same body), or extract it as a variable instead.");
          }
        }
        case MethodInvocation mi
            when mi.getExpression() == null
                || mi.getExpression() instanceof ThisExpression t && t.getQualifier() == null -> {
          List<ITypeBinding> around = typesAround(mi);
          if (around.isEmpty() || !subtype(around.get(0), owner)) {
            throw new Refusal(
                "the call at "
                    + c.where(mi)
                    + " calls "
                    + method
                    + " on an enclosing object, and "
                    + b.getName()
                    + " written there would be looked up in "
                    + (around.isEmpty() ? "another type" : around.get(0).getName())
                    + " first. Write its receiver first ("
                    + owner.getName()
                    + ".this."
                    + method
                    + "(...)), or extract it as a variable instead.");
          }
        }
        case MethodInvocation mi -> {
          Expression on = mi.getExpression();
          boolean assigned = false;
          for (Object a : mi.arguments()) {
            assigned |= writes((Expression) a, on);
          }
          if (!steady(on) || assigned) {
            String text = c.file().text().substring(on.getStartPosition(), Nodes.end(on));
            throw new Refusal(
                "the call at "
                    + c.where(mi)
                    + " calls "
                    + method
                    + " on "
                    + text
                    + ", and would now pass "
                    + v.at(c)
                    + ", evaluating "
                    + text
                    + " again after the arguments, when it could be another object. Put "
                    + text
                    + " in a local first.");
          }
        }
        default -> {}
      }
    }
    String probe = v.written(c, true);
    String text = c.file().text();
    int at =
        c.arguments().isEmpty()
            ? c.open()
            : Nodes.end((ASTNode) c.arguments().get(c.arguments().size() - 1));
    String insert = (c.arguments().isEmpty() ? "" : ", ") + probe;
    int from = at + insert.length() - probe.length();
    int to = at + insert.length();
    CompilationUnit unit =
        Jdt.parse(project, c.file().file(), text.substring(0, at) + insert + text.substring(at));
    List<MethodInvocation> there =
        Nodes.all(unit, MethodInvocation.class).stream()
            .filter(n -> n.getStartPosition() >= from && Nodes.end(n) <= to)
            .toList();
    for (IProblem p : unit.getProblems()) {
      if (p.getID() == IProblem.NotVisibleMethod
          && p.getSourceStart() >= from
          && p.getSourceEnd() < to) {
        String name =
            there.stream()
                .filter(n ->
                    n.getStartPosition() <= p.getSourceStart() && Nodes.end(n) > p.getSourceStart())
                .reduce((first, second) -> second)
                .map(n -> n.getName().getIdentifier())
                .orElse("it");
        throw new Refusal(
            "the call at "
                + c.where(c.node())
                + " would pass "
                + v.at(c)
                + ", which does not compile there: "
                + p.getMessage()
                + ". Widen "
                + name
                + " first, or extract it as a variable instead.");
      }
    }
    for (int i = 0; i < calls.size() && i < there.size(); i++) {
      IMethodBinding mine = calls.get(i).resolveMethodBinding();
      IMethodBinding theirs = there.get(i).resolveMethodBinding();
      if (mine == null
          || theirs == null
          || key(mine).equals(key(theirs))
          || !Modifier.isStatic(mine.getModifiers()) && Instance.overrides(theirs, mine)) {
        continue;
      }
      throw new Refusal(
          "the call at "
              + c.where(c.node())
              + " would pass "
              + v.at(c)
              + ", where "
              + calls.get(i).getName()
              + " calls "
              + Signatures.label(theirs)
              + ", not "
              + Signatures.label(mine)
              + " or an override of it. Rename one of them first, or extract it as a variable"
              + " instead.");
    }
  }
}
