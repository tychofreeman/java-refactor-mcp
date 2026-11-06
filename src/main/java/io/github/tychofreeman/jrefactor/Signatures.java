package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
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
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.RecordPattern;
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
    String value = args.required("value");
    parsesAs("value", value, "class X { Object x = " + value + "; }");
    Opened opened = Opened.of(project, args.required("file"));
    for (Object t : opened.unit().types()) {
      if (t instanceof RecordDeclaration r
          && r.getName().getIdentifier().equals(args.required("name"))
          && r.resolveBinding() != null) {
        return addComponent(project, args, opened, r, value);
      }
    }
    return addParameter(project, args, c -> value);
  }

  /** add_parameter with {@code value} giving the argument each call passes. */
  static Outcome addParameter(SourceProject project, Args args, Function<Call, String> value)
      throws Exception {
    String parameter = args.required("parameter");
    String type = args.required("type");
    if (!SourceVersion.isName(parameter)) {
      throw new Refusal("parameter " + parameter + " is not a valid Java name.");
    }
    parsesAs("type", type, "class X { " + type + " x; }");
    MethodFamily family = MethodFamily.of(project, args);
    int at = family.index(args.string("before"), "before", true);
    family.refuseHiding(parameter);
    SignatureChange change = new SignatureChange(family);
    for (Decl d : family.decls) {
      change.insert(d.file(), d.m().parameters(), d.open(), -1, at, type + " " + parameter);
    }
    for (Call c : family.calls) {
      change.insert(c.file(), c.arguments(), c.open(), c.bare(), at, value.apply(c));
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

  /**
   * change_type for a method: with parameter, that parameter's type; with return_type, the return
   * type; in every declaration of the family, and in a lambda that writes the parameter's type.
   * Calls are not edited: one the new type breaks is refused by the gate.
   */
  static Outcome changeType(SourceProject project, Args args) throws Exception {
    String type = args.required("type");
    parsesAs("type", type, "class X { " + type + " x; }");
    boolean returns = args.flag("return_type");
    Precondition.require(
        Precondition.that(
            () -> returns != args.has("parameter"),
            () ->
                "change_type of a method takes parameter (whose type to change) or return_type=true,"
                    + " not both."));
    MethodFamily family = MethodFamily.of(project, args);
    SignatureChange change = new SignatureChange(family);
    checkPrimitiveUses(family, args, type, returns);
    if (returns) {
      for (Decl d : family.decls) {
        Precondition.require(
            Precondition.that(
                () -> !d.m().isConstructor(),
                () -> family.label + " is a constructor, which has no return type."),
            Precondition.that(
                () -> d.m().extraDimensions().isEmpty(),
                () ->
                    family.label
                        + " writes its return type's [] after its parameters. Write it before the"
                        + " name first."));
        change.replace(d.file(), d.m().getReturnType2(), type);
      }
      return change.apply(
          project,
          "changed the return type of " + family.label + " to " + type,
          args.list("imports"));
    }
    String parameter = args.required("parameter");
    int at = family.index(parameter, "parameter", false);
    for (Decl d : family.decls) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) d.m().parameters().get(at);
      Precondition.require(
          Precondition.that(
              () -> !p.isVarargs(),
              () ->
                  parameter
                      + " of "
                      + family.label
                      + " is varargs. Change it with replace_declaration."),
          Precondition.that(
              () -> p.extraDimensions().isEmpty(),
              () ->
                  parameter
                      + " of "
                      + family.label
                      + " writes its [] after its name. Write it after the type first."));
      change.replace(d.file(), p.getType(), type);
    }
    for (Lambda l : family.lambdas) {
      if (l.typed()) {
        List<String> params = l.params();
        String name =
            ((VariableDeclaration) l.node().parameters().get(at)).getName().getIdentifier();
        params.set(at, type + " " + name);
        change.lambda(l, params);
      }
    }
    return change.apply(
        project,
        "changed the type of " + parameter + " of " + family.label + " to " + type,
        args.list("imports"));
  }

  /**
   * Refuses a change of or to a primitive or boxed type where a use decides the result by the type
   * ({@link PrimitiveUses}): each call's result, for the return type; each use of the parameter in
   * the family's bodies, for a parameter.
   */
  private static void checkPrimitiveUses(
      MethodFamily family, Args args, String type, boolean returns) {
    IMethodBinding target = family.target.resolveBinding();
    if (target == null) {
      return;
    }
    if (returns) {
      ITypeBinding before = target.getReturnType();
      if (!PrimitiveUses.applies(before, type)) {
        return;
      }
      boolean boxed = PrimitiveUses.anyBoxed(before, type);
      for (Call c : family.calls) {
        if (c.node() instanceof Expression x) {
          PrimitiveUses.check(
              x, family.label + "'s result", before.getName(), type, boxed, c.file().rel());
        }
      }
      return;
    }
    String parameter = args.required("parameter");
    int at = family.index(parameter, "parameter", false);
    ITypeBinding before = target.getParameterTypes()[at];
    if (!PrimitiveUses.applies(before, type)) {
      return;
    }
    boolean boxed = PrimitiveUses.anyBoxed(before, type);
    for (Decl d : family.decls) {
      IVariableBinding p =
          ((SingleVariableDeclaration) d.m().parameters().get(at)).resolveBinding();
      if (d.m().getBody() == null || p == null) {
        continue;
      }
      for (SimpleName n : Nodes.all(d.m().getBody(), SimpleName.class)) {
        if (n.resolveBinding() instanceof IVariableBinding v
            && v.getVariableDeclaration().getKey().equals(p.getKey())) {
          PrimitiveUses.check(n, parameter, before.getName(), type, boxed, d.file().rel());
        }
      }
    }
  }

  /** Refuses {@code text} as the {@code what} argument unless {@code wrapped} parses. */
  private static void parsesAs(String what, String text, String wrapped) {
    for (IProblem p : Jdt.parseSyntax(wrapped).getProblems()) {
      if (p.isError()) {
        throw new Refusal(what + " " + text + " does not parse: " + p.getMessage() + ".");
      }
    }
  }

  /**
   * add_parameter on a record whose canonical constructor is implicit or compact: a component, at
   * the end or before {@code before}, and every construction ({@code new Line(...)}, {@code
   * this(...)}) passes {@code value}.
   */
  private static Outcome addComponent(
      SourceProject project, Args args, Opened opened, RecordDeclaration r, String value)
      throws Exception {
    String parameter = args.required("parameter");
    String type = args.required("type");
    if (!SourceVersion.isName(parameter)) {
      throw new Refusal("parameter " + parameter + " is not a valid Java name.");
    }
    parsesAs("type", type, "class X { " + type + " x; }");
    String name = r.getName().getIdentifier();
    List<?> components = r.recordComponents();
    int at = components.size();
    for (int i = 0; i < components.size(); i++) {
      String c = ((SingleVariableDeclaration) components.get(i)).getName().getIdentifier();
      if (c.equals(parameter)) {
        throw new Refusal(name + " already has a component " + parameter + ".");
      }
      if (c.equals(args.string("before"))) {
        at = i;
      }
    }
    if (args.string("before") != null && at == components.size()) {
      throw new Refusal(name + " has no component " + args.string("before") + " to put it before.");
    }
    for (Object o : r.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m
          && m.isConstructor()
          && !m.isCompactConstructor()
          && m.parameters().size() == components.size()) {
        throw new Refusal(
            name
                + " writes its canonical constructor out, and a parameter of it is not a"
                + " component; add_parameter adds a component to a record whose canonical"
                + " constructor is implicit or compact.");
      }
    }
    String key = r.resolveBinding().getErasure().getKey();
    Holding holding = Holding.of(project, name, opened.file());
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    int constructions = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      String text = holding.texts().get(e.getKey());
      List<TextEdit> list = new ArrayList<>();
      for (RecordPattern p : Nodes.all(e.getValue(), RecordPattern.class)) {
        ITypeBinding b = p.getPatternType().resolveBinding();
        if (b != null && b.getErasure().getKey().equals(key)) {
          throw new Refusal(
              "the record pattern at "
                  + project.relative(e.getKey())
                  + " line "
                  + e.getValue().getLineNumber(p.getStartPosition())
                  + " takes every component of "
                  + name
                  + "; it would need one more.");
        }
      }
      for (ClassInstanceCreation c : Nodes.all(e.getValue(), ClassInstanceCreation.class)) {
        IMethodBinding b = c.resolveConstructorBinding();
        if (b != null && b.getDeclaringClass().getErasure().getKey().equals(key)) {
          list.add(
              insertAt(text, c.arguments(), text.indexOf('(', Nodes.end(c.getType())), at, value));
          constructions++;
        }
      }
      for (ConstructorInvocation c : Nodes.all(e.getValue(), ConstructorInvocation.class)) {
        IMethodBinding b = c.resolveConstructorBinding();
        if (b != null && b.getDeclaringClass().getErasure().getKey().equals(key)) {
          list.add(
              insertAt(text, c.arguments(), text.indexOf('(', c.getStartPosition()), at, value));
          constructions++;
        }
      }
      if (e.getKey().equals(opened.file())) {
        list.add(
            insertAt(
                text,
                components,
                text.indexOf('(', Nodes.end(r.getName())),
                at,
                type + " " + parameter));
      }
      if (!list.isEmpty()) {
        edits.put(e.getKey(), list);
      }
    }
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            holding.texts(),
            edits,
            "adding " + type + " " + parameter + " to " + name,
            "Check that " + value + " type-checks as " + type + " at each construction.");
    return new Outcome(
        "added the component "
            + type
            + " "
            + parameter
            + " to "
            + name
            + "; "
            + constructions
            + (constructions == 1 ? " construction passes " : " constructions pass ")
            + value,
        declared);
  }

  /**
   * The edit that puts {@code item} at index {@code at} of {@code list}, whose ( is at {@code
   * open}.
   */
  private static TextEdit insertAt(String text, List<?> list, int open, int at, String item) {
    if (list.isEmpty()) {
      return new TextEdit(open + 1, open + 1, item);
    }
    if (at >= list.size()) {
      int end = Nodes.end((ASTNode) list.get(list.size() - 1));
      return new TextEdit(end, end, ", " + item);
    }
    int start = ((ASTNode) list.get(at)).getStartPosition();
    return new TextEdit(start, start, item + ", ");
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
        out.add(file.text().substring(p.getStartPosition(), Nodes.end(p)));
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

  /**
   * Whether {@code type} is declared in one of the project's source files: one parsed from source
   * (the parse reads only the project's source roots), as a second top-level class of a file is, or
   * one in the file its name implies.
   */
  static boolean projects(SourceProject project, ITypeBinding type) {
    if (type.getErasure().isFromSource()) {
      return true;
    }
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
    return after(text, Nodes.end(node));
  }

  /** The offset just after the first "(" at or after {@code from}. */
  static int after(String text, int from) {
    return text.indexOf('(', from) + 1;
  }
}
