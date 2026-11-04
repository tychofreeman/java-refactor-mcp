package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * Signature changes: a parameter added, removed or moved in every declaration of a method's family
 * (what it overrides and what overrides it) and at every call in the project.
 */
final class Signatures {

  private Signatures() {}

  static Outcome addParameter(SourceProject project, Args args) throws Exception {
    String parameter = args.required("parameter");
    String type = args.required("type");
    String value = args.required("value");
    if (!SourceVersion.isName(parameter)) {
      throw new Refusal("parameter " + parameter + " is not a valid Java name.");
    }
    parsesAs("type", type, "class X { " + type + " x; }");
    parsesAs("value", value, "class X { Object x = " + value + "; }");
    MethodFamily family = MethodFamily.of(project, args);
    int at = family.index(args.string("before"), "before", true);
    family.refuseHiding(parameter);
    SignatureChange change = new SignatureChange(family);
    for (Decl d : family.decls) {
      change.insert(d.file(), d.m().parameters(), d.open(), -1, at, type + " " + parameter);
    }
    for (Call c : family.calls) {
      change.insert(c.file(), c.arguments(), c.open(), c.bare(), at, value);
    }
    for (Lambda l : family.lambdas) {
      List<String> params = l.params();
      params.add(at, l.typed() ? type + " " + parameter : parameter);
      change.lambda(l, params);
    }
    return change.apply(
        project, "added " + type + " " + parameter + " to " + family.label, args.list("imports"));
  }

  static Outcome removeParameter(SourceProject project, Args args) throws Exception {
    MethodFamily family = MethodFamily.of(project, args);
    int at = family.index(args.required("parameter"), "parameter", false);
    family.refuseReading(at);
    for (Lambda l : family.lambdas) {
      VariableDeclaration p = (VariableDeclaration) l.node().parameters().get(at);
      IVariableBinding b = p.resolveBinding();
      boolean[] reads = {false};
      l.node()
          .getBody()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(SimpleName n) {
                  reads[0] |= b != null && b.isEqualTo(n.resolveBinding());
                  return true;
                }
              });
      if (reads[0]) {
        throw new Refusal(
            "the lambda ("
                + l.file().rel()
                + ", line "
                + l.file().line(l.node())
                + ") reads "
                + args.required("parameter")
                + ", so it cannot be removed. Change the lambda not to read it first.");
      }
    }
    for (Call c : family.calls) {
      Expression argument = (Expression) c.arguments().get(at);
      if (!pure(argument)) {
        throw new Refusal(
            "the argument "
                + argument
                + " ("
                + c.where(argument)
                + ") may have side effects, and removing it would drop them. Assign it to a"
                + " variable before the call, or remove the call.");
      }
    }
    SignatureChange change = new SignatureChange(family);
    for (Decl d : family.decls) {
      change.remove(d.file(), d.m().parameters(), at);
      change.removeParamTag(d, at);
    }
    for (Call c : family.calls) {
      change.remove(c.file(), c.arguments(), at);
    }
    for (Lambda l : family.lambdas) {
      List<String> params = l.params();
      params.remove(at);
      change.lambda(l, params);
    }
    return change.apply(
        project, "removed " + args.required("parameter") + " from " + family.label, List.of());
  }

  static Outcome moveParameter(SourceProject project, Args args) throws Exception {
    MethodFamily family = MethodFamily.of(project, args);
    String parameter = args.required("parameter");
    String before = args.string("before");
    int from = family.index(parameter, "parameter", false);
    int to = family.index(before, "before", true);
    if (to == from || to == from + 1) {
      throw new Refusal(parameter + " is already there; nothing to move.");
    }
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < family.size; i++) {
      order.add(i);
    }
    order.remove(from);
    order.add(to > from ? to - 1 : to, from);
    int low = Math.min(from, to);
    int high = Math.max(from, to - 1);
    for (Call c : family.calls) {
      List<Expression> impure = new ArrayList<>();
      for (int i = low; i <= high; i++) {
        Expression argument = (Expression) c.arguments().get(i);
        if (!pure(argument)) {
          impure.add(argument);
        }
      }
      if (impure.size() > 1) {
        throw new Refusal(
            "the arguments "
                + impure.get(0)
                + " and "
                + impure.get(1)
                + " ("
                + c.where(impure.get(0))
                + ") may both have side effects, and moving would change their order. Assign"
                + " one to a variable before the call first.");
      }
    }
    SignatureChange change = new SignatureChange(family);
    for (Decl d : family.decls) {
      change.reorder(d.file(), d.m().parameters(), order);
    }
    for (Call c : family.calls) {
      change.reorder(c.file(), c.arguments(), order);
    }
    for (Lambda l : family.lambdas) {
      List<String> params = l.params();
      List<String> moved = new ArrayList<>();
      for (int i : order) {
        moved.add(params.get(i));
      }
      change.lambda(l, moved);
    }
    return change.apply(
        project,
        "moved "
            + parameter
            + (before == null ? " to last" : " before " + before)
            + " in "
            + family.label,
        List.of());
  }

  /** Refuses {@code text} as the {@code what} argument unless {@code wrapped} parses. */
  private static void parsesAs(String what, String text, String wrapped) {
    for (IProblem p : Jdt.parseSyntax(wrapped).getProblems()) {
      if (p.isError()) {
        throw new Refusal(what + " " + text + " does not parse: " + p.getMessage() + ".");
      }
    }
  }

  /** A file of the project, read and parsed with bindings. */
  record Source(Path file, String rel, String text, CompilationUnit unit) {
    int line(ASTNode node) {
      return unit.getLineNumber(node.getStartPosition());
    }
  }

  /** A declaration in the family; {@code open} is the offset just after its "(". */
  record Decl(Source file, MethodDeclaration m, IMethodBinding binding, int open) {}

  /**
   * A call of the family: its arguments, the offset just after its "(", or -1 for an enum constant
   * without one, then {@code bare} is where "(...)" goes.
   */
  record Call(Source file, ASTNode node, List<?> arguments, int open, int bare) {
    String where(ASTNode n) {
      return file.rel() + ", line " + file.line(n);
    }
  }

  /** A lambda that implements a family member: one more declaration whose parameters change. */
  record Lambda(Source file, LambdaExpression node) {
    /** Each parameter's text, as written: {@code a}, or {@code int a} when they are typed. */
    List<String> params() {
      List<String> out = new ArrayList<>();
      for (Object o : node.parameters()) {
        ASTNode p = (ASTNode) o;
        out.add(file.text().substring(p.getStartPosition(), end(p)));
      }
      return out;
    }

    /** Whether its parameters are written with their types. */
    boolean typed() {
      return !node.parameters().isEmpty()
          && node.parameters().get(0) instanceof SingleVariableDeclaration;
    }
  }

  /**
   * The binding keys of {@code binding}'s family in {@code units}: every declaration it overrides
   * or that overrides it, transitively. Refuses when one of them overrides a library method:
   * "{@code Name.compareTo(Name) overrides compareTo in java.lang.Comparable, which is not the
   * project's, }" then {@code consequence}.
   */
  static Set<String> family(
      SourceProject project,
      Collection<CompilationUnit> units,
      IMethodBinding binding,
      String consequence) {
    Map<String, Set<String>> links = new HashMap<>();
    Map<String, IMethodBinding> declared = new HashMap<>();
    Map<String, IMethodBinding> library = new HashMap<>();
    for (CompilationUnit unit : units) {
      unit.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration m) {
              IMethodBinding b = m.resolveBinding();
              if (b != null) {
                String k = key(b);
                declared.put(k, b);
                for (IMethodBinding over : overridden(b)) {
                  String o = key(over);
                  links.computeIfAbsent(k, x -> new HashSet<>()).add(o);
                  links.computeIfAbsent(o, x -> new HashSet<>()).add(k);
                  if (!projects(project, over.getDeclaringClass())) {
                    library.put(k, over);
                  }
                }
              }
              return true;
            }
          });
    }
    Set<String> keys = new TreeSet<>();
    Deque<String> todo = new ArrayDeque<>(List.of(key(binding)));
    while (!todo.isEmpty()) {
      String k = todo.pop();
      if (keys.add(k)) {
        todo.addAll(links.getOrDefault(k, Set.of()));
      }
    }
    for (String k : keys) {
      IMethodBinding over = library.get(k);
      if (over != null) {
        throw new Refusal(
            label(declared.get(k))
                + " overrides "
                + over.getName()
                + " in "
                + over.getDeclaringClass().getErasure().getQualifiedName()
                + ", which is not the project's, "
                + consequence);
      }
    }
    return keys;
  }

  /** The methods of supertypes that {@code b} overrides. */
  private static List<IMethodBinding> overridden(IMethodBinding b) {
    List<IMethodBinding> out = new ArrayList<>();
    if (b.isConstructor() || b.getDeclaringClass() == null) {
      return out;
    }
    Deque<ITypeBinding> todo = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    ITypeBinding start = b.getDeclaringClass();
    if (start.getSuperclass() != null) {
      todo.add(start.getSuperclass());
    }
    todo.addAll(List.of(start.getInterfaces()));
    while (!todo.isEmpty()) {
      ITypeBinding t = todo.pop();
      if (!seen.add(t.getErasure().getKey())) {
        continue;
      }
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (b.overrides(m)) {
          out.add(m);
        }
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return out;
  }

  /** Whether {@code type} is declared in one of the project's source files. */
  static boolean projects(SourceProject project, ITypeBinding type) {
    ITypeBinding top = type.getErasure();
    while (top.getDeclaringClass() != null) {
      top = top.getDeclaringClass();
    }
    String path = top.getQualifiedName().replace('.', '/') + ".java";
    for (Path root : project.sourceRoots()) {
      if (Files.isRegularFile(root.resolve(path))) {
        return true;
      }
    }
    return false;
  }

  /** The key that names {@code b}'s declaration in every file's bindings. */
  static String key(IMethodBinding b) {
    return b.getMethodDeclaration().getKey();
  }

  /**
   * "Outer.Cart.add(String, int)": the method's class with its enclosing types, and its parameter
   * types.
   */
  static String label(IMethodBinding b) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : b.getMethodDeclaration().getParameterTypes()) {
      types.add(t.getErasure().getName());
    }
    return Names.typeLabel(b.getDeclaringClass())
        + "."
        + b.getName()
        + "("
        + String.join(", ", types)
        + ")";
  }

  /** Whether evaluating {@code e} can have no side effect: names, literals, this, and the like. */
  private static boolean pure(Expression e) {
    return switch (e) {
      case Name n -> true;
      case NumberLiteral n -> true;
      case StringLiteral n -> true;
      case CharacterLiteral n -> true;
      case BooleanLiteral n -> true;
      case NullLiteral n -> true;
      case TypeLiteral n -> true;
      case TextBlock n -> true;
      case ThisExpression n -> true;
      case LambdaExpression n -> true;
      case MethodReference n -> true;
      case FieldAccess f -> pure(f.getExpression());
      case ParenthesizedExpression p -> pure(p.getExpression());
      case CastExpression c -> pure(c.getExpression());
      case InstanceofExpression i -> pure(i.getLeftOperand());
      case PrefixExpression p ->
          p.getOperator() != PrefixExpression.Operator.INCREMENT
              && p.getOperator() != PrefixExpression.Operator.DECREMENT
              && pure(p.getOperand());
      case InfixExpression i ->
          pure(i.getLeftOperand()) && pure(i.getRightOperand()) && allPure(i.extendedOperands());
      case ConditionalExpression c ->
          pure(c.getExpression()) && pure(c.getThenExpression()) && pure(c.getElseExpression());
      default -> false;
    };
  }

  private static boolean allPure(List<?> expressions) {
    for (Object o : expressions) {
      if (!pure((Expression) o)) {
        return false;
      }
    }
    return true;
  }

  /** The offset just after the first "(" following {@code node}. */
  static int open(String text, ASTNode node) {
    return after(text, end(node));
  }

  /** The offset just after the first "(" at or after {@code from}. */
  static int after(String text, int from) {
    return text.indexOf('(', from) + 1;
  }

  static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }
}
