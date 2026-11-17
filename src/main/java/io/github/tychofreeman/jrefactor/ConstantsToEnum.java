package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * extract_type with {@code members} the {@code static final} constants of a class or interface:
 * they become the constants of a new enum in a file of its own, and every field, local, parameter,
 * record component and method result that holds one of them takes the enum's type. A use is allowed
 * by a list (a copy, {@code ==}, {@code equals}, a switch); any other use sees the old value and is
 * refused, or with {@code accessor} reads it back through the enum's accessor.
 */
final class ConstantsToEnum {
  private final SourceProject project;
  private final String enumName;
  private final String accessor;
  private final boolean valueSemantics;
  private final boolean strings;

  /** Each constant's variable key to its name, and each value to the constant that has it. */
  private final Map<String, String> constants = new LinkedHashMap<>();

  private final Map<Object, String> byValue = new HashMap<>();

  private final Map<Path, CompilationUnit> units;
  private final Map<Path, String> texts;

  /** The project's variables, methods, reads of each variable and calls of each method, by key. */
  private final Map<String, VariableDeclaration> vars = new HashMap<>();

  private final Map<String, MethodDeclaration> methods = new HashMap<>();
  private final Map<String, List<Expression>> refs = new HashMap<>();
  private final Map<String, List<ASTNode>> calls = new HashMap<>();
  private final List<ASTNode> functional = new ArrayList<>();

  /** What the closure found: expressions whose value becomes the enum's, and the holders. */
  private final Set<ASTNode> values = new HashSet<>();

  private final Set<String> holders = new HashSet<>();
  private final Set<String> holderMethods = new HashSet<>();
  private final Set<String> holderParameters = new HashSet<>();
  private final Set<ASTNode> retype = new LinkedHashSet<>();
  private final Set<ASTNode> declarations = new HashSet<>();
  private final List<String> retyped = new ArrayList<>();
  private final Map<Expression, String> literals = new LinkedHashMap<>();
  private final List<Expression> wraps = new ArrayList<>();
  private final List<VariableDeclarationFragment> zeroed = new ArrayList<>();
  private boolean nullSeen;
  private boolean publicParameter;

  private ConstantsToEnum(
      SourceProject project,
      String enumName,
      String accessor,
      boolean valueSemantics,
      boolean strings,
      Holding all) {
    this.project = project;
    this.enumName = enumName;
    this.accessor = accessor;
    this.valueSemantics = valueSemantics;
    this.strings = strings;
    this.units = all.units();
    this.texts = all.texts();
  }

  /** Whether {@code members} name a static field of the class or interface {@code name}. */
  static boolean applies(Opened opened, String name, List<String> members) {
    TypeDeclaration type = typeNamed(opened, name);
    if (type == null) {
      return false;
    }
    for (FieldDeclaration f : type.getFields()) {
      for (Object o : f.fragments()) {
        if (members.contains(((VariableDeclarationFragment) o).getName().getIdentifier())
            && (type.isInterface() || Modifier.isStatic(f.getModifiers()))) {
          return true;
        }
      }
    }
    return false;
  }

  private static TypeDeclaration typeNamed(Opened opened, String name) {
    for (Object t : opened.unit().types()) {
      if (t instanceof TypeDeclaration type && type.getName().getIdentifier().equals(name)) {
        return type;
      }
    }
    return null;
  }

  static Outcome run(SourceProject project, Args args, Opened opened) throws Exception {
    String name = args.required("name");
    String enumName = args.required("new_type");
    String accessor = args.string("accessor");
    List<String> members = args.list("members");
    Precondition.require(Precondition.identifier(enumName, "the enum"));
    if (accessor != null) {
      Precondition.require(Precondition.identifier(accessor, "the accessor"));
    }
    Path target = opened.file().resolveSibling(enumName + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(target),
            () -> project.relative(target) + " already exists. Choose another name."));
    TypeDeclaration owner = typeNamed(opened, name);
    String text = opened.text();
    List<FieldDeclaration> declared = new ArrayList<>();
    List<String> order = new ArrayList<>();
    String type = null;
    Map<String, Object> valueOf = new LinkedHashMap<>();
    Map<String, String> keyOf = new LinkedHashMap<>();
    for (String m : members) {
      FieldDeclaration fd = null;
      VariableDeclarationFragment frag = null;
      for (FieldDeclaration f : owner.getFields()) {
        for (Object o : f.fragments()) {
          if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(m)) {
            fd = f;
            frag = (VariableDeclarationFragment) o;
          }
        }
      }
      IVariableBinding b = frag == null ? null : frag.resolveBinding();
      if (b == null
          || !Modifier.isStatic(b.getModifiers())
          || !Modifier.isFinal(b.getModifiers())) {
        throw new Refusal(
            m
                + " is not a static final constant of "
                + name
                + ". members names the constants that become the enum's.");
      }
      if (fd.fragments().size() != 1) {
        throw new Refusal(
            "the declaration of " + m + " declares several constants. Split it first.");
      }
      String t = b.getType().getQualifiedName();
      if (!t.equals("int") && !t.equals("java.lang.String")) {
        throw new Refusal(
            m + " is " + b.getType().getName() + "; constants of int or String become an enum.");
      }
      if (type != null && !type.equals(t)) {
        throw new Refusal(
            "the constants are of two types, "
                + simple(type)
                + " and "
                + b.getType().getName()
                + "; name constants of one type.");
      }
      type = t;
      if (!literal(frag.getInitializer()) || b.getConstantValue() == null) {
        throw new Refusal(
            "the value of "
                + m
                + " is not a literal; each constant that becomes the enum's is given one.");
      }
      Object v = b.getConstantValue();
      for (Map.Entry<String, Object> e : valueOf.entrySet()) {
        if (e.getValue().equals(v)) {
          throw new Refusal(
              e.getKey()
                  + " and "
                  + m
                  + " are both "
                  + Nodes.source(text, frag.getInitializer())
                  + "; an enum value would not say which it is. Leave one out of members.");
        }
      }
      valueOf.put(m, v);
      keyOf.put(m, b.getVariableDeclaration().getKey());
      declared.add(fd);
    }
    declared.sort((a, b) -> a.getStartPosition() - b.getStartPosition());
    for (FieldDeclaration fd : declared) {
      order.add(((VariableDeclarationFragment) fd.fragments().get(0)).getName().getIdentifier());
    }
    ConstantsToEnum c =
        new ConstantsToEnum(
            project,
            enumName,
            accessor,
            args.flag("value_semantics"),
            type.equals("java.lang.String"),
            Holding.where(project, (f, t) -> true));
    for (String m : order) {
      c.constants.put(keyOf.get(m), m);
      c.byValue.put(valueOf.get(m), m);
    }
    c.index();
    for (String key : c.constants.keySet()) {
      for (Expression e : c.refs.getOrDefault(key, List.of())) {
        c.value(e);
      }
    }
    c.checkDeclarations();
    c.checkNull();

    String step = " ".repeat(Indent.step(text, 2));
    String created = c.enumText(opened, owner, declared, order, step);
    List<String> imports = args.list("imports");
    String fix = "Nothing was changed.";
    // Read from the parsed text before any file changes.
    String notes = c.literalNotes();
    Edits.newFile(project, target, created, enumName, imports, fix);
    Map<Path, List<TextEdit>> edits = c.edits();
    edits.computeIfAbsent(opened.file(), k -> new ArrayList<>());
    for (FieldDeclaration fd : declared) {
      edits.get(opened.file()).add(MemberMove.removal(text, fd));
    }
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            c.texts,
            edits,
            imports,
            "turning " + Names.listed(order) + " into the enum " + enumName,
            fix);
    List<String> changed = new ArrayList<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      if (!e.getKey().equals(opened.file()) && !e.getValue().isEmpty()) {
        changed.add(project.relative(e.getKey()).toString());
      }
    }
    changed.sort(null);
    return new Outcome(
        "made "
            + Names.listed(order)
            + " of "
            + name
            + " the enum "
            + enumName
            + " ("
            + project.relative(target)
            + ")"
            + (c.retyped.isEmpty() ? "" : "; retyped " + Names.listed(c.retyped))
            + (changed.isEmpty() ? "" : "; changed " + Names.listed(changed))
            + notes
            + (c.wraps.isEmpty()
                ? ""
                : "; read the value as "
                    + accessor
                    + "() at "
                    + Names.plural(c.wraps.size(), "place"))
            + written.said(),
        written.declared());
  }

  private static String simple(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  /** Whether {@code e} is a number or string literal, or a negated number. */
  private static boolean literal(Expression e) {
    return e instanceof NumberLiteral
        || e instanceof StringLiteral
        || (e instanceof PrefixExpression p
            && p.getOperator() == PrefixExpression.Operator.MINUS
            && p.getOperand() instanceof NumberLiteral);
  }

  /** Indexes every file's variables, methods, reads and calls by key. */
  private void index() {
    for (CompilationUnit unit : units.values()) {
      for (VariableDeclaration d : Nodes.all(unit, VariableDeclaration.class)) {
        if (d.resolveBinding() != null) {
          vars.put(d.resolveBinding().getVariableDeclaration().getKey(), d);
        }
      }
      for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
        if (m.resolveBinding() != null) {
          methods.put(MethodFamily.key(m.resolveBinding()), m);
        }
      }
      for (SimpleName n : Nodes.all(unit, SimpleName.class)) {
        if (!n.isDeclaration()
            && !(n.getParent() instanceof VariableDeclaration d && d.getName() == n)
            && Nodes.ancestor(n, ImportDeclaration.class) == null
            && n.resolveBinding() instanceof IVariableBinding b) {
          refs.computeIfAbsent(b.getVariableDeclaration().getKey(), k -> new ArrayList<>())
              .add(read(n));
        }
      }
      for (ASTNode n : Nodes.all(unit, ASTNode.class)) {
        if (n instanceof LambdaExpression || n instanceof MethodReference) {
          functional.add(n);
        } else if (Calls.binding(n) instanceof IMethodBinding b) {
          calls.computeIfAbsent(MethodFamily.key(b), k -> new ArrayList<>()).add(n);
        }
      }
    }
  }

  /**
   * The expression a read of {@code n} is: {@code Order.OPEN}, {@code this.state}, or {@code n}.
   */
  private static Expression read(SimpleName n) {
    ASTNode p = n.getParent();
    if (p instanceof QualifiedName q && q.getName() == n) {
      return q;
    }
    if (p instanceof FieldAccess f && f.getName() == n) {
      return f;
    }
    if (p instanceof SuperFieldAccess f && f.getName() == n) {
      return f;
    }
    return n;
  }

  /** Where {@code n} is, for a refusal: "line 12 of com/example/Order.java". */
  private String where(ASTNode n) {
    CompilationUnit unit = (CompilationUnit) n.getRoot();
    return "line " + unit.getLineNumber(n.getStartPosition()) + " of " + relative(unit);
  }

  private String relative(CompilationUnit unit) {
    return project.relative(Jdt.fileOf(unit)).toString();
  }

  private String names() {
    return Names.listed(new ArrayList<>(constants.values()));
  }

  /** {@code x} gives one of the enum's values: what is done with it decides what else does. */
  private void value(Expression x) {
    if (!values.add(x)) {
      return;
    }
    if (Nodes.ancestor(x, Annotation.class) != null) {
      throw new Refusal(
          Nodes.show(x)
              + " is in an annotation at "
              + where(x)
              + ", where an enum value is not a constant. Write the value there first, with"
              + " replace_expression.");
    }
    ASTNode p = x.getParent();
    switch (p) {
      case ParenthesizedExpression e -> value(e);
      case ConditionalExpression c when c.getExpression() != x -> {
        Expression other =
            c.getThenExpression() == x ? c.getElseExpression() : c.getThenExpression();
        join(other, "chooses %s beside " + Nodes.show(x));
        value(c);
      }
      case VariableDeclarationFragment f when f.getInitializer() == x -> hold(f.resolveBinding());
      case Assignment a -> {
        if (a.getOperator() != Assignment.Operator.ASSIGN) {
          throw written(x, a.getOperator().toString());
        }
        if (a.getRightHandSide() == x) {
          if (target(a.getLeftHandSide()) instanceof IVariableBinding b) {
            hold(b);
          } else {
            throw new Refusal(
                Nodes.show(x)
                    + " is assigned to "
                    + Nodes.show(a.getLeftHandSide())
                    + " at "
                    + where(x)
                    + ", which is not a variable that can hold "
                    + enumName
                    + ". Change that use first.");
          }
        } else {
          join(a.getRightHandSide(), "assigns %s to " + Nodes.show(x));
        }
        if (!(a.getParent() instanceof ExpressionStatement)) {
          value(a);
        }
      }
      case PrefixExpression e
          when e.getOperator() == PrefixExpression.Operator.INCREMENT
              || e.getOperator() == PrefixExpression.Operator.DECREMENT ->
          throw written(x, e.getOperator().toString());
      case PostfixExpression e -> throw written(x, e.getOperator().toString());
      case ReturnStatement r -> {
        ASTNode around = r;
        while (!(around instanceof MethodDeclaration) && !(around instanceof LambdaExpression)) {
          around = around.getParent();
        }
        if (around instanceof MethodDeclaration m && m.resolveBinding() != null) {
          holdMethod(m.resolveBinding());
        } else {
          throw new Refusal(
              Nodes.show(x)
                  + " is returned from a lambda at "
                  + where(x)
                  + ", whose type the tool does not change yet. Change that use first.");
        }
      }
      case MethodInvocation m when m.getExpression() == x -> {
        if (m.getName().getIdentifier().equals("equals") && m.arguments().size() == 1) {
          compare(x, (Expression) m.arguments().get(0));
        } else {
          seen(x, "the object of ." + m.getName() + "()");
        }
      }
      case InfixExpression i
          when (i.getOperator() == InfixExpression.Operator.EQUALS
                  || i.getOperator() == InfixExpression.Operator.NOT_EQUALS)
              && !i.hasExtendedOperands() ->
          compare(x, i.getLeftOperand() == x ? i.getRightOperand() : i.getLeftOperand());
      case InfixExpression i ->
          seen(
              x,
              "an operand of "
                  + (i.getOperator() == InfixExpression.Operator.PLUS
                          && i.resolveTypeBinding() != null
                          && i.resolveTypeBinding().getQualifiedName().equals("java.lang.String")
                      ? "string +"
                      : i.getOperator().toString()));
      case SwitchStatement s when s.getExpression() == x -> labels(s.statements(), x);
      case SwitchExpression s when s.getExpression() == x -> labels(s.statements(), x);
      case SwitchCase c -> {
        ASTNode sw = c.getParent();
        join(
            sw instanceof SwitchStatement s
                ? s.getExpression()
                : ((SwitchExpression) sw).getExpression(),
            "switches on %s with case " + Nodes.show(x));
      }
      case ExpressionStatement e -> {}
      default -> {
        if (x.getLocationInParent().isChildListProperty() && Calls.binding(p) != null) {
          argument(x, p, Calls.binding(p));
        } else {
          seen(x, "used in " + Nodes.show(p));
        }
      }
    }
  }

  /** The variable an assignment's left side names, or null. */
  private static IVariableBinding target(Expression lhs) {
    return switch (Nodes.unparenthesized(lhs)) {
      case Name n when n.resolveBinding() instanceof IVariableBinding b -> b;
      case FieldAccess f -> f.resolveFieldBinding();
      case SuperFieldAccess f -> f.resolveFieldBinding();
      default -> null;
    };
  }

  private Refusal written(Expression x, String operator) {
    return new Refusal(
        Nodes.show(x)
            + " is changed by "
            + operator
            + " at "
            + where(x)
            + ", and an enum value is not counted. Change that use first.");
  }

  /** {@code x} is an argument of the call {@code call}, which runs {@code m}. */
  private void argument(Expression x, ASTNode call, IMethodBinding m) {
    List<?> args = arguments(call);
    int i = args.indexOf(x);
    if (m.getName().equals("equals")
        && m.getDeclaringClass().getErasure().getQualifiedName().equals("java.util.Objects")
        && args.size() == 2) {
      compare(x, (Expression) args.get(1 - i));
      return;
    }
    if (call instanceof MethodInvocation mi
        && m.getName().equals("equals")
        && args.size() == 1
        && mi.getExpression() != null) {
      compare(x, mi.getExpression());
      return;
    }
    if (m.isConstructor() && m.getDeclaringClass().isRecord() && !methods.containsKey(key(m))) {
      component(m.getDeclaringClass(), i);
      return;
    }
    if (!methods.containsKey(key(m))) {
      seen(
          x,
          "passed to "
              + m.getDeclaringClass().getErasure().getName()
              + (m.isConstructor() ? "" : "." + m.getName()));
      return;
    }
    holdParameter(m, i, x);
  }

  private static List<?> arguments(ASTNode call) {
    return switch (call) {
      case MethodInvocation m -> m.arguments();
      case SuperMethodInvocation m -> m.arguments();
      case ClassInstanceCreation c -> c.arguments();
      case ConstructorInvocation c -> c.arguments();
      case SuperConstructorInvocation c -> c.arguments();
      case EnumConstantDeclaration c -> c.arguments();
      default -> List.of();
    };
  }

  private static String key(IMethodBinding m) {
    return MethodFamily.key(m);
  }

  /** {@code x} is compared with {@code other}, which must then give an enum value too. */
  private void compare(Expression x, Expression other) {
    join(other, "compares %s with " + Nodes.show(x));
  }

  /**
   * {@code e} must give one of the enum's values: a constant, a literal equal to one, null for a
   * string, or what a holder gives, which makes a variable or method it reads a holder. {@code how}
   * says what the code does with it, for a refusal ({@code %s} is {@code e}).
   */
  private void join(Expression e, String how) {
    e = Nodes.unparenthesized(e);
    if (values.contains(e)) {
      return;
    }
    switch (e) {
      case NullLiteral n when strings -> nullSeen = true;
      case ConditionalExpression c -> {
        join(c.getThenExpression(), how);
        join(c.getElseExpression(), how);
      }
      case Expression l
          when literal(l) && byValue.containsKey(l.resolveConstantExpressionValue()) ->
          literals.put(l, byValue.get(l.resolveConstantExpressionValue()));
      case Name n
          when n.resolveBinding() instanceof IVariableBinding b
              && !constants.containsKey(b.getVariableDeclaration().getKey()) ->
          hold(b);
      case FieldAccess f when f.resolveFieldBinding() != null -> hold(f.resolveFieldBinding());
      case MethodInvocation m
          when m.resolveMethodBinding() != null && projects(m.resolveMethodBinding()) ->
          holdMethod(m.resolveMethodBinding());
      case Name n when n.resolveBinding() instanceof IVariableBinding b -> {}
      default ->
          throw new Refusal(
              where(e)
                  + " "
                  + how.replace("%s", Nodes.show(e))
                  + ", which may be a value that none of "
                  + names()
                  + " has. Convert it first in a method that returns one of the constants for each"
                  + " value (if (raw == 0) return "
                  + constants.values().iterator().next()
                  + "; ...); that method is then retyped.");
    }
  }

  private boolean projects(IMethodBinding m) {
    return methods.containsKey(key(m))
        || (m.getDeclaringClass().isRecord() && m.getParameterTypes().length == 0);
  }

  /** The case labels of a switch over {@code x}: each a constant, or a literal equal to one. */
  private void labels(List<?> statements, Expression x) {
    for (Object o : statements) {
      if (o instanceof SwitchCase c) {
        for (Object l : c.expressions()) {
          Expression e = (Expression) l;
          if (!(e instanceof Name n
              && n.resolveBinding() instanceof IVariableBinding b
              && constants.containsKey(b.getVariableDeclaration().getKey()))) {
            if (!literal(e)) {
              throw new Refusal(
                  "case "
                      + Nodes.show(e)
                      + " at "
                      + where(e)
                      + " is not one of "
                      + names()
                      + "; a switch over "
                      + Nodes.show(x)
                      + " can have only those. Change that case first.");
            }
            join(e, "switches on " + Nodes.show(x) + " with case %s");
          }
        }
      }
    }
  }

  /** {@code x} is used where the old value is seen: refused, or read through the accessor. */
  private void seen(Expression x, String role) {
    if (accessor == null) {
      throw new Refusal(
          Nodes.show(x)
              + " is "
              + role
              + " at "
              + where(x)
              + ", where "
              + enumName
              + " would not mean the value it holds now. Pass accessor (such as code) to keep the"
              + " value there as "
              + Nodes.show(x)
              + ".code(), or change that use first.");
    }
    wraps.add(x);
  }

  /** The variable {@code b} holds an enum value: its declaration is retyped, its reads followed. */
  private void hold(IVariableBinding b) {
    String key = b.getVariableDeclaration().getKey();
    if (constants.containsKey(key) || holders.contains(key)) {
      return;
    }
    VariableDeclaration d = vars.get(key);
    if (d == null && b.isField() && b.getDeclaringClass().isRecord()) {
      for (CompilationUnit unit : units.values()) {
        for (RecordDeclaration r : Nodes.all(unit, RecordDeclaration.class)) {
          if (r.resolveBinding() != null
              && Bindings.same(r.resolveBinding(), b.getDeclaringClass())) {
            for (int i = 0; i < r.recordComponents().size(); i++) {
              if (((SingleVariableDeclaration) r.recordComponents().get(i))
                  .getName()
                  .getIdentifier()
                  .equals(b.getName())) {
                component(r.resolveBinding(), i);
                return;
              }
            }
          }
        }
      }
    }
    if (d == null) {
      throw new Refusal(
          b.getName()
              + " is a field of "
              + b.getDeclaringClass().getName()
              + ", which is not the project's, so its type cannot change. Change that use first.");
    }
    ASTNode p = d.getParent();
    if (p instanceof MethodDeclaration m) {
      holdParameter(m.resolveBinding(), m.parameters().indexOf(d), d);
      return;
    }
    if (p instanceof RecordDeclaration r) {
      component(r.resolveBinding(), r.recordComponents().indexOf(d));
      return;
    }
    if (!(p instanceof FieldDeclaration
        || p instanceof VariableDeclarationStatement
        || p instanceof VariableDeclarationExpression)) {
      throw new Refusal(
          b.getName()
              + " at "
              + where(d)
              + " is declared by "
              + Nodes.show(p)
              + ", whose type the tool does not change yet. Change that use first.");
    }
    holders.add(key);
    declarations.add(p);
    Type type = declaredType(p);
    checkType(type, d, b.getName());
    if (p instanceof FieldDeclaration f) {
      checkField(f, (VariableDeclarationFragment) d, b);
      retyped.add(Names.typeLabel(b.getDeclaringClass()) + "." + b.getName());
    } else {
      MethodDeclaration m = Nodes.ancestor(d, MethodDeclaration.class);
      retyped.add(
          "local "
              + b.getName()
              + (m == null || m.resolveBinding() == null
                  ? ""
                  : " in " + Names.methodLabel(m.resolveBinding())));
    }
    if (d.getInitializer() != null) {
      join(d.getInitializer(), "assigns %s to " + b.getName());
    }
    for (Expression e : refs.getOrDefault(key, List.of())) {
      value(e);
    }
  }

  private static Type declaredType(ASTNode declaration) {
    return switch (declaration) {
      case FieldDeclaration f -> f.getType();
      case VariableDeclarationStatement s -> s.getType();
      case VariableDeclarationExpression s -> s.getType();
      case SingleVariableDeclaration s -> s.getType();
      case MethodDeclaration m -> m.getReturnType2();
      default -> null;
    };
  }

  /** Refuses a holder not declared as the constants' type; {@code var} stays as it is. */
  private void checkType(Type type, ASTNode at, String what) {
    if (type != null && type.isVar()) {
      return;
    }
    boolean fits =
        type != null
            && (at instanceof VariableDeclaration v ? v.getExtraDimensions() == 0 : true)
            && (strings
                ? type.resolveBinding() != null
                    && type.resolveBinding().getQualifiedName().equals("java.lang.String")
                : type instanceof PrimitiveType p && p.getPrimitiveTypeCode() == PrimitiveType.INT);
    if (!fits) {
      throw new Refusal(
          what
              + " at "
              + where(at)
              + " is declared "
              + (type == null ? "without a type" : Nodes.show(type))
              + " and holds one of "
              + names()
              + "; only "
              + (strings ? "a String" : "an int")
              + " can become "
              + enumName
              + ". Change its type first (change_type), or that use.");
    }
    retype.add(type);
  }

  /** A field reflection may read, or an int field that starts as 0, is refused or given a value. */
  private void checkField(FieldDeclaration f, VariableDeclarationFragment d, IVariableBinding b) {
    boolean annotated = false;
    for (Object m : f.modifiers()) {
      annotated |= m instanceof Annotation;
    }
    if (!valueSemantics
        && (annotated || Hierarchy.isA(b.getDeclaringClass(), "java.io.Serializable"))) {
      throw new Refusal(
          b.getName()
              + " is "
              + (annotated
                  ? "annotated"
                  : "a field of " + b.getDeclaringClass().getName() + ", which is Serializable")
              + ", so reflection may read it by its type. Pass value_semantics=true to accept that it"
              + " then holds "
              + enumName
              + ", or keep the value out of it.");
    }
    if (d.getInitializer() == null && !strings && !Modifier.isFinal(f.getModifiers())) {
      if (!byValue.containsKey(0)) {
        throw new Refusal(
            b.getName()
                + " starts as 0, which is none of the constants. Give it an initializer first (one"
                + " of "
                + names()
                + "), with replace_declaration.");
      }
      zeroed.add(d);
    }
  }

  /** Parameter {@code i} of {@code m}'s family holds an enum value: every call passes one. */
  private void holdParameter(IMethodBinding m, int i, ASTNode at) {
    if (m.isVarargs() && i == m.getParameterTypes().length - 1) {
      throw new Refusal(
          Names.methodLabel(m)
              + " takes the value at "
              + where(at)
              + " as varargs, which the tool does not change yet. Change that use first.");
    }
    for (String k :
        MethodFamily.family(
            project, units.values(), m.getMethodDeclaration(), "so its parameter cannot change.")) {
      if (!holderParameters.add(k + "#" + i)) {
        continue;
      }
      refuseFunctional(k, Names.methodLabel(m));
      MethodDeclaration decl = methods.get(k);
      if (decl == null) {
        continue;
      }
      SingleVariableDeclaration p = (SingleVariableDeclaration) decl.parameters().get(i);
      IVariableBinding b = p.resolveBinding();
      String pk = b.getVariableDeclaration().getKey();
      holders.add(pk);
      checkType(p.getType(), p, p.getName().getIdentifier());
      retyped.add(
          "parameter "
              + p.getName().getIdentifier()
              + " of "
              + Names.methodLabel(decl.resolveBinding()));
      if (Modifier.isPublic(decl.getModifiers())
          || decl.resolveBinding().getDeclaringClass().isInterface()) {
        publicParameter = true;
      }
      for (ASTNode call : calls.getOrDefault(k, List.of())) {
        List<?> args = arguments(call);
        if (i < args.size()) {
          join((Expression) args.get(i), "passes %s to " + Names.methodLabel(m));
        }
      }
      for (Expression e : refs.getOrDefault(pk, List.of())) {
        value(e);
      }
    }
  }

  /**
   * The method {@code m}'s family returns an enum value: its returns give one, its calls are one.
   */
  private void holdMethod(IMethodBinding m) {
    if (m.getDeclaringClass().isRecord()
        && !methods.containsKey(key(m))
        && m.getParameterTypes().length == 0) {
      ITypeBinding r = m.getDeclaringClass();
      for (IVariableBinding f : r.getDeclaredFields()) {
        if (f.getName().equals(m.getName())) {
          hold(f);
        }
      }
      return;
    }
    for (String k :
        MethodFamily.family(
            project, units.values(), m.getMethodDeclaration(), "so its type cannot change.")) {
      if (!holderMethods.add(k)) {
        continue;
      }
      refuseFunctional(k, Names.methodLabel(m));
      MethodDeclaration decl = methods.get(k);
      if (decl == null) {
        continue;
      }
      checkType(decl.getReturnType2(), decl, Names.methodLabel(decl.resolveBinding()));
      retyped.add(
          Names.typeLabel(decl.resolveBinding().getDeclaringClass()) + "." + decl.getName() + "()");
      if (decl.getBody() != null) {
        for (ReturnStatement r : Nodes.all(decl.getBody(), ReturnStatement.class)) {
          if (r.getExpression() != null && Nodes.enclosing(r, MethodDeclaration.class) == decl) {
            join(r.getExpression(), "returns %s from " + Names.methodLabel(decl.resolveBinding()));
          }
        }
      }
      for (ASTNode call : calls.getOrDefault(k, List.of())) {
        if (call instanceof Expression e) {
          value(e);
        }
      }
    }
  }

  /** Refuses a lambda or method reference that implements or names the method {@code k}. */
  private void refuseFunctional(String k, String label) {
    for (ASTNode f : functional) {
      // What it implements, and for a method reference what it names.
      IMethodBinding sam =
          ((Expression) f).resolveTypeBinding() == null
              ? null
              : ((Expression) f).resolveTypeBinding().getFunctionalInterfaceMethod();
      IMethodBinding named = f instanceof MethodReference ? Calls.binding(f) : null;
      if ((sam != null && key(sam).equals(k)) || (named != null && key(named).equals(k))) {
        throw new Refusal(
            "a "
                + (f instanceof LambdaExpression ? "lambda" : "method reference")
                + " at "
                + where(f)
                + " is "
                + label
                + ", whose type the tool does not change in a lambda yet. Change that use first.");
      }
    }
  }

  /**
   * Component {@code i} of the record {@code r} holds an enum value: with value_semantics, its type
   * changes, its reads and accessor calls follow, and every construction passes one.
   */
  private void component(ITypeBinding r, int i) {
    RecordDeclaration decl = null;
    for (CompilationUnit unit : units.values()) {
      for (RecordDeclaration d : Nodes.all(unit, RecordDeclaration.class)) {
        if (d.resolveBinding() != null && Bindings.same(d.resolveBinding(), r)) {
          decl = d;
        }
      }
    }
    if (decl == null) {
      throw new Refusal(
          r.getName() + " is not a record of the project's, so its components cannot change.");
    }
    SingleVariableDeclaration c = (SingleVariableDeclaration) decl.recordComponents().get(i);
    String name = c.getName().getIdentifier();
    if (!valueSemantics) {
      throw new Refusal(
          r.getName()
              + " is a record, whose generated toString and hashCode would change with "
              + name
              + "'s type. Pass value_semantics=true to accept that, or keep the value out of the"
              + " record.");
    }
    IVariableBinding field = null;
    for (IVariableBinding f : decl.resolveBinding().getDeclaredFields()) {
      if (f.getName().equals(name)) {
        field = f;
      }
    }
    String key = field.getVariableDeclaration().getKey();
    if (!holders.add(key)) {
      return;
    }
    for (Object o : decl.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m
          && (m.isConstructor()
              || (m.getName().getIdentifier().equals(name) && m.parameters().isEmpty()))) {
        throw new Refusal(
            r.getName()
                + " declares "
                + (m.isConstructor() ? "a constructor" : name + "()")
                + ", which the tool does not retype with "
                + name
                + " yet. Change that use first.");
      }
    }
    checkType(c.getType(), c, name);
    retyped.add(Names.typeLabel(r) + "." + name);
    for (IMethodBinding m : decl.resolveBinding().getDeclaredMethods()) {
      if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
        for (ASTNode call : calls.getOrDefault(key(m), List.of())) {
          if (call instanceof Expression e) {
            value(e);
          }
        }
      }
      if (m.isConstructor() && m.getParameterTypes().length == decl.recordComponents().size()) {
        for (ASTNode call : calls.getOrDefault(key(m), List.of())) {
          join((Expression) arguments(call).get(i), "passes %s to " + r.getName());
        }
      }
    }
    for (Expression e : refs.getOrDefault(key, List.of())) {
      value(e);
    }
    for (Expression e :
        refs.getOrDefault(c.resolveBinding().getVariableDeclaration().getKey(), List.of())) {
      value(e);
    }
  }

  /** Refuses a declaration of several variables of which only some hold an enum value. */
  private void checkDeclarations() {
    for (ASTNode p : declarations) {
      List<?> fragments =
          switch (p) {
            case FieldDeclaration f -> f.fragments();
            case VariableDeclarationStatement s -> s.fragments();
            case VariableDeclarationExpression s -> s.fragments();
            default -> List.of();
          };
      for (Object o : fragments) {
        VariableDeclarationFragment f = (VariableDeclarationFragment) o;
        if (!holders.contains(f.resolveBinding().getVariableDeclaration().getKey())) {
          throw new Refusal(
              f.getName()
                  + " at "
                  + where(f)
                  + " is declared with a variable that becomes "
                  + enumName
                  + ", and does not hold one itself. Split the declaration first.");
        }
      }
    }
  }

  /**
   * Reports, or refuses under preserve_exceptions, an accessor call on a value that may be null.
   */
  private void checkNull() {
    if (accessor != null && strings && !wraps.isEmpty() && (nullSeen || publicParameter)) {
      Exceptional.change(
          project,
          Nodes.show(wraps.get(0))
              + "."
              + accessor
              + "() at "
              + where(wraps.get(0))
              + " throws NullPointerException where the value is null, where it used the null"
              + " before",
          "leave out accessor, or keep null out of the values first.");
    }
  }

  /** The edits of every file: constants named through the enum, holders retyped. */
  private Map<Path, List<TextEdit>> edits() {
    Map<Path, List<TextEdit>> out = new LinkedHashMap<>();
    for (String key : constants.keySet()) {
      for (Expression e : refs.getOrDefault(key, List.of())) {
        edit(out, e, constant(e, constants.get(key)));
      }
    }
    for (Map.Entry<Expression, String> l : literals.entrySet()) {
      edit(out, l.getKey(), constant(l.getKey(), l.getValue()));
    }
    for (ASTNode t : retype) {
      edit(out, t, enumName);
    }
    for (Expression w : wraps) {
      boolean bare =
          w instanceof Name
              || w instanceof FieldAccess
              || w instanceof MethodInvocation
              || w instanceof ParenthesizedExpression;
      Path f = Jdt.fileOf((CompilationUnit) w.getRoot());
      List<TextEdit> list = out.computeIfAbsent(f, k -> new ArrayList<>());
      if (!bare) {
        list.add(new TextEdit(w.getStartPosition(), w.getStartPosition(), "("));
      }
      list.add(new TextEdit(Nodes.end(w), Nodes.end(w), (bare ? "" : ")") + "." + accessor + "()"));
    }
    String zero = byValue.get(0);
    for (VariableDeclarationFragment d : zeroed) {
      int at = Nodes.end(d.getName());
      Path f = Jdt.fileOf((CompilationUnit) d.getRoot());
      out.computeIfAbsent(f, k -> new ArrayList<>())
          .add(new TextEdit(at, at, " = " + enumName + "." + zero));
    }
    // A single static import of a constant names a field that is gone.
    for (Map.Entry<Path, CompilationUnit> u : units.entrySet()) {
      String text = texts.get(u.getKey());
      for (Object o : u.getValue().imports()) {
        ImportDeclaration i = (ImportDeclaration) o;
        if (i.isStatic()
            && !i.isOnDemand()
            && i.resolveBinding() instanceof IVariableBinding b
            && constants.containsKey(b.getVariableDeclaration().getKey())) {
          int start = Lines.start(text, i.getStartPosition());
          int end = Lines.end(text, Nodes.end(i) - 1);
          // The blank line after a group the import was alone in goes with it.
          int next = text.indexOf('\n', end);
          if (next >= 0 && text.substring(end, next).isBlank()) {
            end = next + 1;
          }
          out.computeIfAbsent(u.getKey(), k -> new ArrayList<>()).add(new TextEdit(start, end, ""));
        }
      }
    }
    return out;
  }

  /** A constant as written at {@code e}: bare as a case label, else through the enum. */
  private String constant(Expression e, String name) {
    return e.getParent() instanceof SwitchCase ? name : enumName + "." + name;
  }

  private static void edit(Map<Path, List<TextEdit>> out, ASTNode n, String text) {
    Path f = Jdt.fileOf((CompilationUnit) n.getRoot());
    out.computeIfAbsent(f, k -> new ArrayList<>())
        .add(new TextEdit(n.getStartPosition(), Nodes.end(n), text));
  }

  /** "; wrote OrderState.OPEN for 0 at line 9 of ...", for each literal replaced. */
  private String literalNotes() {
    StringBuilder out = new StringBuilder();
    for (Map.Entry<Expression, String> l : literals.entrySet()) {
      out.append("; wrote ")
          .append(constant(l.getKey(), l.getValue()))
          .append(" for ")
          .append(Nodes.show(l.getKey()))
          .append(" at ")
          .append(where(l.getKey()));
    }
    return out.toString();
  }

  /**
   * The new enum's file: the constants in their order, with their Javadoc and, with accessor,
   * values.
   */
  private String enumText(
      Opened opened,
      TypeDeclaration owner,
      List<FieldDeclaration> declared,
      List<String> order,
      String step) {
    String text = opened.text();
    StringBuilder out = new StringBuilder(opened.packageHeader());
    out.append(Modifier.isPublic(owner.getModifiers()) ? "public " : "")
        .append("enum ")
        .append(enumName)
        .append(" {\n");
    for (int i = 0; i < declared.size(); i++) {
      FieldDeclaration fd = declared.get(i);
      if (fd.getJavadoc() != null) {
        String indent = Lines.indentOf(text, fd.getStartPosition());
        for (String line : Nodes.source(text, fd.getJavadoc()).split("\n", -1)) {
          out.append(step)
              .append(line.startsWith(indent) ? line.substring(indent.length()) : line)
              .append("\n");
        }
      }
      VariableDeclarationFragment frag = (VariableDeclarationFragment) fd.fragments().get(0);
      out.append(step).append(order.get(i));
      if (accessor != null) {
        out.append("(").append(Nodes.source(text, frag.getInitializer())).append(")");
      }
      out.append(i < declared.size() - 1 ? ",\n" : accessor != null ? ";\n" : "\n");
    }
    if (accessor != null) {
      String t = strings ? "String" : "int";
      out.append("\n")
          .append(step)
          .append("private final ")
          .append(t)
          .append(" ")
          .append(accessor)
          .append(";\n\n")
          .append(step)
          .append(enumName)
          .append("(")
          .append(t)
          .append(" ")
          .append(accessor)
          .append(") {\n")
          .append(step)
          .append(step)
          .append("this.")
          .append(accessor)
          .append(" = ")
          .append(accessor)
          .append(";\n")
          .append(step)
          .append("}\n\n")
          .append(step)
          .append("public ")
          .append(t)
          .append(" ")
          .append(accessor)
          .append("() {\n")
          .append(step)
          .append(step)
          .append("return ")
          .append(accessor)
          .append(";\n")
          .append(step)
          .append("}\n");
    }
    return out.append("}\n").toString();
  }
}
