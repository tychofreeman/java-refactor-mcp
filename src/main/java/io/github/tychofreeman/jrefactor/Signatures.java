package io.github.tychofreeman.jrefactor;

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
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.RecordPattern;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * Signature changes: a parameter added, removed or moved in every declaration of a method's family
 * (what it overrides and what overrides it) and at every call in the project.
 */
final class Signatures {

  /** The types whose {@code toString} a string's {@code +} calls without effect. */
  private static final Set<String> PLAIN_TO_STRING =
      Set.of(
          "java.lang.String",
          "java.lang.Boolean",
          "java.lang.Byte",
          "java.lang.Character",
          "java.lang.Short",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Float",
          "java.lang.Double");

  private Signatures() {}

  static Outcome addParameter(SourceProject project, Args args) throws Exception {
    Precondition.require(
        Precondition.that(
            () -> args.has("parameter") && args.has("type"),
            () ->
                "add_parameter needs parameter and type, the new parameter's name and its Java"
                    + " type; or type_parameter, to add a type parameter to a class."));
    Outcome field = EnumField.add(project, args);
    if (field != null) {
      return field;
    }
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
    return addParameter(project, args, c -> value, (family, change) -> {});
  }

  /**
   * add_parameter with {@code value} giving the argument each call passes; {@code more} checks the
   * family and adds its own edits to the change before any is made.
   */
  static Outcome addParameter(
      SourceProject project,
      Args args,
      Function<Call, String> value,
      BiConsumer<MethodFamily, SignatureChange> more)
      throws Exception {
    String parameter = args.required("parameter");
    String type = args.required("type");
    checkNewParameter(parameter, type);
    MethodFamily family = MethodFamily.of(project, args);
    int at = family.index(args.string("before"), "before", true);
    family.refuseHiding(parameter);
    SignatureChange change = new SignatureChange(family);
    more.accept(family, change);
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

  /**
   * Refuses a new parameter's name {@code parameter} or its {@code type} when it does not parse.
   */
  static void checkNewParameter(String parameter, String type) {
    Precondition.require(Precondition.identifier(parameter, "the new parameter"));
    parsesAs("type", type, "class X { " + type + " x; }");
  }

  static Outcome removeParameter(SourceProject project, Args args) throws Exception {
    MethodFamily family = MethodFamily.of(project, args);
    int at = family.index(args.required("parameter"), "parameter", false);
    String value = args.string("value");
    if (value != null) {
      return removeWithValue(project, family, at, args.required("parameter"), value.strip());
    }
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
      if (acts(argument)) {
        throw new Refusal(
            "the argument "
                + argument
                + " ("
                + c.where(argument)
                + ") may have side effects, and removing it would drop them. Assign it to a"
                + " variable before the call, or remove the call.");
      }
      if (mayThrow(argument)) {
        Exceptional.change(
            project,
            "the argument "
                + argument
                + " ("
                + c.where(argument)
                + ") may throw, and removing it drops that",
            "assign it to a variable before the call, or keep the parameter.");
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
        if (acts(argument)) {
          impure.add(argument);
        }
      }
      Expression moved = (Expression) c.arguments().get(from);
      for (int i = low; i <= high; i++) {
        Expression other = (Expression) c.arguments().get(i);
        if (i != from
            && (mayThrow(moved) && (mayThrow(other) || acts(other))
                || mayThrow(other) && acts(moved))) {
          Exceptional.change(
              project,
              "the arguments "
                  + other
                  + " and "
                  + moved
                  + " ("
                  + c.where(moved)
                  + ") now run in the other order, and one may throw: what has run when it"
                  + " throws, or which exception it is, changes",
              "assign one to a variable before the call first.");
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
        requireReturnType(d, family);
        change.replace(d.file(), d.m().getReturnType2(), type);
      }
      return checked(
          project,
          family,
          change,
          "changed the return type of " + family.label + " to " + type,
          "changing the return type of " + family.label + " to " + type,
          ChangeType.wildcardFix(
              type,
              family.target.getReturnType2().toString(),
              "what it returns",
              SignatureChange.FIX),
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
    return checked(
        project,
        family,
        change,
        "changed the type of " + parameter + " of " + family.label + " to " + type,
        "changing the type of " + parameter + " of " + family.label + " to " + type,
        ChangeType.wildcardFix(
            type,
            ((SingleVariableDeclaration) family.target.parameters().get(at)).getType().toString(),
            parameter,
            SignatureChange.FIX),
        args.list("imports"));
  }

  /**
   * Refuses a change of the return type of {@code d}, a declaration of {@code family}: a
   * constructor, or a return type with [] after the parameters.
   */
  static void requireReturnType(Decl d, MethodFamily family) {
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
  }

  /**
   * Refuses a change of or to a primitive or boxed type where a use decides the result by the type
   * ({@link PrimitiveUses}): each call's result, for the return type; each use of the parameter in
   * the family's bodies, for a parameter.
   */
  static void checkPrimitiveUses(MethodFamily family, Args args, String type, boolean returns) {
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
  static void parsesAs(String what, String text, String wrapped) {
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
    checkNewParameter(parameter, type);
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
              SignatureChange.insertAt(
                  c.arguments(), text.indexOf('(', Nodes.end(c.getType())) + 1, at, value));
          constructions++;
        }
      }
      for (ConstructorInvocation c : Nodes.all(e.getValue(), ConstructorInvocation.class)) {
        IMethodBinding b = c.resolveConstructorBinding();
        if (b != null && b.getDeclaringClass().getErasure().getKey().equals(key)) {
          list.add(
              SignatureChange.insertAt(
                  c.arguments(), text.indexOf('(', c.getStartPosition()) + 1, at, value));
          constructions++;
        }
      }
      if (e.getKey().equals(opened.file())) {
        list.add(
            SignatureChange.insertAt(
                components,
                text.indexOf('(', Nodes.end(r.getName())) + 1,
                at,
                type + " " + parameter));
      }
      if (!list.isEmpty()) {
        edits.put(e.getKey(), list);
      }
    }
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            holding.texts(),
            edits,
            List.of(),
            "adding " + type + " " + parameter + " to " + name,
            "Check that " + value + " type-checks as " + type + " at each construction.");
    Stubs.Declared declared = written.declared();
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
            + value
            + written.said(),
        declared);
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
        out.add(Nodes.source(file.text(), p));
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
                for (IMethodBinding over : Hierarchy.overridden(b)) {
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
            Names.methodLabel(declared.get(k))
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

  /**
   * Whether {@code type} is declared in one of the project's source files: one parsed from source
   * (the parse reads only the project's source roots), as a second top-level class of a file is, or
   * one in the file its name implies.
   */
  static boolean projects(SourceProject project, ITypeBinding type) {
    if (type.getErasure().isFromSource()) {
      return true;
    }
    return project.fileOf(type) != null;
  }

  /** The key that names {@code b}'s declaration in every file's bindings. */
  static String key(IMethodBinding b) {
    return b.getMethodDeclaration().getKey();
  }

  /** The offset just after the first "(" following {@code node}. */
  static int open(String text, ASTNode node) {
    return after(text, Nodes.end(node));
  }

  /** The offset just after the first "(" at or after {@code from}. */
  static int after(String text, int from) {
    return text.indexOf('(', from) + 1;
  }

  /**
   * Removes the parameter at {@code at} of a method nothing overrides, which every call passes
   * {@code value} for: a field of the method's object or a static one, or a literal. Each read of
   * the parameter in the body becomes a read of that.
   */
  private static Outcome removeWithValue(
      SourceProject project, MethodFamily family, int at, String parameter, String value)
      throws Exception {
    if (family.decls.size() != 1 || !family.lambdas.isEmpty()) {
      throw new Refusal(
          "value applies to a method that nothing overrides or implements, as each declaration"
              + " could need another value. Remove the parameter from the others first, or"
              + " without value.");
    }
    Decl d = family.decls.get(0);
    MethodDeclaration m = d.m();
    IVariableBinding param = ((SingleVariableDeclaration) m.parameters().get(at)).resolveBinding();
    boolean literal =
        value.matches("-?[0-9][0-9_.xXa-fA-FlLdDfF]*|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)+'")
            || List.of("true", "false", "null").contains(value);
    if (!literal && !fieldName(m, value)) {
      return removeWithExpression(project, family, at, parameter, value);
    }
    IVariableBinding field = literal ? null : fieldNamed(family, d, at, value);
    if (field != null
        && !Modifier.isStatic(field.getModifiers())
        && Modifier.isStatic(m.getModifiers())) {
      throw new Refusal(
          value
              + " is a field of an instance, which the static method "
              + m.getName()
              + " has none of. Pass a static field or a literal.");
    }
    for (Call c : family.calls) {
      Expression argument = (Expression) c.arguments().get(at);
      if (!(literal ? argument.toString().equals(value) : sameField(argument, c.node(), field))) {
        throw new Refusal(
            "the call at "
                + c.where(argument)
                + " passes "
                + argument
                + ", not "
                + value
                + ", so the parameter cannot become "
                + value
                + ". Remove it without value after changing that call, or keep it.");
      }
    }
    if (!sameType(family, at, param, field)) {
      return removeWithExpression(project, family, at, parameter, value);
    }
    SignatureChange change = new SignatureChange(family);
    String read = literal ? value : readOf(m, field, value);
    for (SimpleName n : Nodes.all(m.getBody(), SimpleName.class)) {
      if (param != null && n.resolveBinding() instanceof IVariableBinding v && v.isEqualTo(param)) {
        if (Nodes.written(n)) {
          throw new Refusal(
              parameter
                  + " is assigned in the body (line "
                  + d.file().line(n)
                  + "), so its reads are not all "
                  + value
                  + ". Assign a local instead first.");
        }
        if (!n.getIdentifier().equals(read)) {
          change.replace(d.file(), n, read);
        }
      }
    }
    change.remove(d.file(), m.parameters(), at);
    change.removeParamTag(d, at);
    for (Call c : family.calls) {
      change.remove(c.file(), c.arguments(), at);
    }
    return change.apply(
        project,
        "removed " + parameter + " from " + family.label + "; its body reads " + read,
        List.of());
  }

  /**
   * The field {@code value} names: {@code f} or {@code this.f}, of the method's class or one above
   * it, or {@code T.F}, a static field one of the calls passes.
   */
  private static IVariableBinding fieldNamed(MethodFamily family, Decl d, int at, String value) {
    String name = value.startsWith("this.") ? value.substring(5) : value;
    if (!name.contains(".")) {
      for (ITypeBinding t = d.binding().getDeclaringClass(); t != null; t = t.getSuperclass()) {
        for (IVariableBinding f : t.getDeclaredFields()) {
          if (f.getName().equals(name)) {
            return f;
          }
        }
      }
    } else {
      String simple = name.substring(name.lastIndexOf('.') + 1);
      String type = name.substring(0, name.lastIndexOf('.'));
      for (Call c : family.calls) {
        if (c.arguments().get(at) instanceof Name n
            && n.resolveBinding() instanceof IVariableBinding f
            && f.isField()
            && Modifier.isStatic(f.getModifiers())
            && f.getName().equals(simple)
            && f.getDeclaringClass() != null
            && (f.getDeclaringClass().getName().equals(type)
                || f.getDeclaringClass().getQualifiedName().equals(type))) {
          return f;
        }
      }
    }
    throw new Refusal(
        value
            + " is not a field of "
            + d.binding().getDeclaringClass().getName()
            + " or a literal. Pass a field (out, this.out, Config.OUT) or a literal, as the calls"
            + " write it.");
  }

  /**
   * Whether {@code argument} reads {@code field}, of the same object as the method the call {@code
   * call} makes when the field is an instance's: unqualified or through this, with the call on
   * this.
   */
  private static boolean sameField(Expression argument, ASTNode call, IVariableBinding field) {
    IBinding b =
        switch (argument) {
          case Name n -> n.resolveBinding();
          case FieldAccess f -> f.resolveFieldBinding();
          default -> null;
        };
    if (!(b instanceof IVariableBinding v) || !Bindings.same(v, field)) {
      return false;
    }
    if (Modifier.isStatic(field.getModifiers())) {
      return true;
    }
    boolean ownArgument =
        argument instanceof SimpleName
            || argument instanceof FieldAccess f
                && f.getExpression() instanceof ThisExpression t
                && t.getQualifier() == null;
    boolean ownCall =
        call instanceof MethodInvocation mi
            && (mi.getExpression() == null
                || mi.getExpression() instanceof ThisExpression t && t.getQualifier() == null);
    return ownArgument && ownCall;
  }

  /**
   * How the body of {@code m} reads {@code field}: by its name when that name finds it there (the
   * body declares no such name, and the field is the first of that name in the method's class or a
   * superclass), else as {@code Type.NAME} or {@code this.name}.
   */
  private static String readOf(MethodDeclaration m, IVariableBinding field, String value) {
    boolean hidden =
        Nodes.all(m.getBody(), VariableDeclaration.class).stream()
            .anyMatch(v -> v.getName().getIdentifier().equals(field.getName()));
    ITypeBinding at = m.resolveBinding().getDeclaringClass();
    if (!hidden && field.getKey().equals(firstNamed(at, field.getName()))) {
      return field.getName();
    }
    return Modifier.isStatic(field.getModifiers())
        ? Extract.typeName(field.getDeclaringClass(), at) + "." + field.getName()
        : "this." + field.getName();
  }

  /** A method reference that implements a family member, and the method it refers to. */
  record Reference(Source file, MethodReference node, IMethodBinding target) {}

  /**
   * Applies {@code change}, a change of a type in {@code family}'s declarations, and refuses it
   * when a call in the family's files then runs another declaration ({@link CallTargets}).
   */
  private static Outcome checked(
      SourceProject project,
      MethodFamily family,
      SignatureChange change,
      String done,
      String doing,
      String fix,
      List<String> imports)
      throws Exception {
    CallTargets targets =
        CallTargets.of(family.sources.values().stream().map(Source::unit).toList());
    Outcome outcome = change.apply(project, done, imports, fix, -1);
    targets.require(project, doing);
    return outcome;
  }

  /**
   * The constructor a construction runs: {@code b} itself, or for an anonymous class ({@code new
   * T(...) {...}}, an enum constant with a body), whose own constructor {@code b} is, the
   * constructor of its superclass that the arguments go to: the one with {@code b}'s parameter
   * types, else the only one that takes as many. Null when there is none.
   */
  static IMethodBinding constructed(IMethodBinding b) {
    if (b == null || !b.getDeclaringClass().isAnonymous()) {
      return b;
    }
    ITypeBinding superclass = b.getDeclaringClass().getSuperclass();
    if (superclass == null) {
      return null;
    }
    List<IMethodBinding> sameCount = new ArrayList<>();
    for (IMethodBinding m : superclass.getDeclaredMethods()) {
      if (!m.isConstructor() || m.getParameterTypes().length != b.getParameterTypes().length) {
        continue;
      }
      sameCount.add(m);
      boolean same = true;
      for (int i = 0; i < m.getParameterTypes().length; i++) {
        same &=
            m.getParameterTypes()[i]
                .getErasure()
                .getKey()
                .equals(b.getParameterTypes()[i].getErasure().getKey());
      }
      if (same) {
        return m;
      }
    }
    return sameCount.size() == 1 ? sameCount.get(0) : null;
  }

  /**
   * Removes the parameter at {@code at} of a method nothing overrides, which every call passes
   * {@code value} for with its own arguments in place of the other parameters: the body then starts
   * with a local of the parameter's name and type, set to {@code value}.
   */
  private static Outcome removeWithExpression(
      SourceProject project, MethodFamily family, int at, String parameter, String value)
      throws Exception {
    Decl d = family.decls.get(0);
    MethodDeclaration m = d.m();
    SingleVariableDeclaration p = (SingleVariableDeclaration) m.parameters().get(at);
    if (m.getBody() == null) {
      throw new Refusal(
          family.label
              + " has no body to compute "
              + value
              + " in. Remove the parameter without value, or keep it.");
    }
    if (p.isVarargs() || p.getExtraDimensions() > 0) {
      throw new Refusal(
          parameter
              + " is declared with ... or with [] after its name, which a local set to "
              + value
              + " cannot be. Declare it as an array type first (change_type), or keep it.");
    }
    ParameterValue.InMethod v = ParameterValue.inMethod(project, d, at, value, family.label);
    for (Effects.Effect x : Effects.of(v.expression()).all()) {
      if (x.deferred() || x.kind() == Effects.Kind.WRITE) {
        throw new Refusal(
            "value "
                + value
                + " assigns, or holds code that runs later ("
                + x.node()
                + "), and may only read and call: "
                + family.label.substring(0, family.label.indexOf('('))
                + "'s other parameters, fields, constants and literals, with operators and"
                + " calls. Keep the parameter.");
      }
    }
    int start = ParameterValue.afterConstructorCall(m);
    if (start != m.getBody().getStartPosition() + 1) {
      ASTNode first = (ASTNode) m.getBody().statements().get(0);
      String call = first instanceof ConstructorInvocation ? "this(...)" : "super(...)";
      IVariableBinding param = p.resolveBinding();
      for (SimpleName n : Nodes.all(first, SimpleName.class)) {
        if (param != null
            && n.resolveBinding() instanceof IVariableBinding b
            && b.isEqualTo(param)) {
          throw new Refusal(
              family.label
                  + " passes "
                  + parameter
                  + " to "
                  + call
                  + ", and a local cannot be declared before that. Change the "
                  + call
                  + " call not to read it first, or keep the parameter.");
        }
      }
    }
    if (m.isConstructor() && v.acts()) {
      throw new Refusal(
          value
              + " would run after the constructor above this one and the field initializers,"
              + " not before them. Keep the parameter, or compute it in a static method that"
              + " calls the constructor.");
    }
    if (m.isConstructor() && !v.fixed()) {
      throw new Refusal(
          value
              + " reads a field, which the constructor above this one or a field's initializer"
              + " could change before a local in the body reads it. Keep the parameter.");
    }
    Set<Integer> uses = v.uses();
    int low = Math.min(at, uses.stream().min(Integer::compare).orElse(at));
    int high = Math.max(at, uses.stream().max(Integer::compare).orElse(at));
    for (Call c : family.calls) {
      Expression argument = (Expression) c.arguments().get(at);
      if (!ParameterValue.passes(v, c, argument)) {
        String here = v.inPlace(c);
        throw new Refusal(
            "the call at "
                + c.where(argument)
                + " passes "
                + argument
                + (here.equals(Nodes.source(c.file().text(), argument))
                    ? ", in which a name means something else than in the method (a member of"
                        + " another object than the one the method runs on, or another"
                        + " overload)"
                    : ", not "
                        + value
                        + (here.equals(value) ? "" : " (with that call's arguments: " + here + ")"))
                + ", so "
                + parameter
                + " cannot become "
                + value
                + ". Change that call first, or keep the parameter.");
      }
      for (int i : uses) {
        Expression passed = (Expression) c.arguments().get(i);
        String name = ((SingleVariableDeclaration) m.parameters().get(i)).getName().getIdentifier();
        if (!ParameterValue.quiet(passed)) {
          throw new Refusal(
              "the call at "
                  + c.where(passed)
                  + " passes "
                  + passed
                  + " for "
                  + name
                  + ", and "
                  + value
                  + " would then be computed from one "
                  + passed
                  + " where the call ran two. Put "
                  + passed
                  + " in a local first.");
        }
        ITypeBinding declared =
            ((SingleVariableDeclaration) m.parameters().get(i)).getType().resolveBinding();
        ITypeBinding given = passed.resolveTypeBinding();
        if (declared != null
            && (given == null || declared.isPrimitive() || given.isPrimitive())
            && (given == null || !declared.getName().equals(given.getName()))) {
          throw new Refusal(
              "the call at "
                  + c.where(passed)
                  + " passes "
                  + passed
                  + (given == null ? "" : " (" + given.getName() + ")")
                  + " for "
                  + name
                  + " ("
                  + declared.getName()
                  + "): the call computed "
                  + argument
                  + " in that type, and the method would compute "
                  + value
                  + " in the parameter's. Keep the parameter, or pass "
                  + name
                  + " in its own type first.");
        }
        if (v.acts() && !Effects.of(passed).fixed() && (i > at || !v.readsFirst(i))) {
          throw new Refusal(
              "the call at "
                  + c.where(passed)
                  + " passes "
                  + passed
                  + " for "
                  + name
                  + ", which a call in "
                  + value
                  + " could change: the call read it again as it computed "
                  + argument
                  + ", and the method would compute "
                  + value
                  + " from the one read. Put "
                  + passed
                  + " in a local first.");
        }
      }
      for (int i = 0; i < c.arguments().size(); i++) {
        Expression other = (Expression) c.arguments().get(i);
        if (i == at || uses.contains(i)) {
          continue;
        }
        boolean quiet = ParameterValue.quiet(other);
        if (!quiet && i > low && i < high) {
          throw new Refusal(
              "the call at "
                  + c.where(other)
                  + " runs "
                  + other
                  + " between the arguments "
                  + value
                  + " is computed from and "
                  + argument
                  + ", which could then differ from "
                  + value
                  + " computed in the method. Put "
                  + other
                  + " in a local first.");
        }
        boolean late =
            v.acts() ? i > at && !Effects.of(other).fixed() : i > high && !quiet && !v.fixed();
        if (late) {
          throw new Refusal(
              "the call at "
                  + c.where(other)
                  + " passes "
                  + other
                  + " after "
                  + argument
                  + "; in the method, "
                  + value
                  + (v.acts() ? " would run after " : " would be computed after ")
                  + other
                  + ", not before it. Put "
                  + other
                  + " in a local first, or move the parameter last (move_parameter).");
        }
      }
    }
    if (v.mayThrow()) {
      Exceptional.change(
          project,
          value
              + " may throw: each call computed it among its arguments, and "
              + family.label.substring(0, family.label.indexOf('('))
              + " now computes it first in its body, after every argument has run",
          "keep the parameter.");
    }
    SignatureChange change = new SignatureChange(family);
    String text = d.file().text();
    String local = Nodes.source(text, p) + " = " + value + ";";
    declareFirst(change, d.file(), m, local);
    change.remove(d.file(), m.parameters(), at);
    change.removeParamTag(d, at);
    for (Call c : family.calls) {
      change.remove(c.file(), c.arguments(), at);
    }
    Outcome done =
        change.apply(project, "removed " + parameter + " from " + family.label, List.of());
    return new Outcome(
        done.text() + "; its body starts with " + local.substring(0, local.length() - 1),
        done.declared());
  }

  /**
   * Makes {@code statement} the first of {@code m}'s body, after its {@code this(...)} or {@code
   * super(...)}: on its own line, a body written on one line opened up for it.
   */
  private static void declareFirst(
      SignatureChange change, Source file, MethodDeclaration m, String statement) {
    String text = file.text();
    int from = ParameterValue.afterConstructorCall(m);
    int close = Nodes.end(m.getBody()) - 1;
    int next = from;
    while (next < close && Character.isWhitespace(text.charAt(next))) {
      next++;
    }
    String outer = Lines.indentOf(text, m.getName().getStartPosition());
    String inner = outer + " ".repeat(Indent.step(text, 2));
    if (text.substring(from, next).contains("\n")) {
      int line = Lines.start(text, next);
      String indent = next == close ? inner : Lines.indentOf(text, next);
      change.replaceRange(file, line, line, indent + statement + "\n");
      return;
    }
    boolean oneLine = !text.substring(from, close).contains("\n");
    if (next == close) {
      change.replaceRange(file, from, close, "\n" + inner + statement + "\n" + outer);
      return;
    }
    change.replaceRange(file, from, next, "\n" + inner + statement + "\n" + inner);
    if (oneLine) {
      int last = close;
      while (Character.isWhitespace(text.charAt(last - 1))) {
        last--;
      }
      change.replaceRange(file, last, close, "\n" + outer);
    }
  }

  /**
   * Whether {@code value} is written as a field is: a name, with {@code this.} or a type before it,
   * that does not start with one of {@code m}'s parameters.
   */
  private static boolean fieldName(MethodDeclaration m, String value) {
    if (!value.matches("(this\\s*\\.\\s*)?[\\w$]+(\\s*\\.\\s*[\\w$]+)*")) {
      return false;
    }
    String first = value.split("\\s*\\.\\s*")[0];
    for (Object o : m.parameters()) {
      if (((SingleVariableDeclaration) o).getName().getIdentifier().equals(first)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether {@code value}, a literal or {@code field}, has the type of {@code param} at every call,
   * so that each read of the parameter can become a read of it and compute the same.
   */
  private static boolean sameType(
      MethodFamily family, int at, IVariableBinding param, IVariableBinding field) {
    if (param == null) {
      return false;
    }
    String key = param.getType().getKey();
    if (field != null) {
      return field.getType().getKey().equals(key);
    }
    for (Call c : family.calls) {
      ITypeBinding t = ((Expression) c.arguments().get(at)).resolveTypeBinding();
      if (t == null || !t.getKey().equals(key)) {
        return false;
      }
    }
    return !family.calls.isEmpty();
  }

  /** The key of the first field named {@code name} in {@code t} or a superclass; null when none. */
  private static String firstNamed(ITypeBinding t, String name) {
    for (; t != null; t = t.getSuperclass()) {
      for (IVariableBinding f : t.getDeclaredFields()) {
        if (f.getName().equals(name)) {
          return f.getKey();
        }
      }
    }
    return null;
  }

  /**
   * Whether evaluating {@code e} changes state where it stands (outside lambdas and anonymous
   * classes): a write, a call, a new object or array, or a string's {@code +} of an object, which
   * calls its {@code toString}.
   */
  private static boolean acts(Expression e) {
    for (Effects.Effect x : Effects.of(e).all()) {
      if (!x.deferred() && (x.changes() || x.kind() == Effects.Kind.NEW_ARRAY)) {
        return true;
      }
    }
    for (InfixExpression i : Nodes.all(e, InfixExpression.class)) {
      ITypeBinding t = i.resolveTypeBinding();
      if (i.getOperator() != InfixExpression.Operator.PLUS
          || t == null
          || !t.getQualifiedName().equals("java.lang.String")
          || Effects.later(i, e) != null) {
        continue;
      }
      List<Expression> operands = new ArrayList<>(List.of(i.getLeftOperand(), i.getRightOperand()));
      for (Object o : i.extendedOperands()) {
        operands.add((Expression) o);
      }
      for (Expression o : operands) {
        ITypeBinding ot = o.resolveTypeBinding();
        if (ot == null
            || !(ot.isPrimitive()
                || ot.isNullType()
                || PLAIN_TO_STRING.contains(ot.getQualifiedName()))) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether evaluating {@code e} may throw where it stands, by {@link Effects#mayThrow()}. */
  private static boolean mayThrow(Expression e) {
    return !Effects.of(e).mayThrow().isEmpty();
  }
}
