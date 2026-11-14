package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * loop_to_stream: an enhanced for over a collection becomes a stream pipeline that runs the same
 * code for each element in the same order, as a sequential stream runs each element through every
 * stage before the next. The body is read from the outside in: an if with no else that holds the
 * rest is a filter; a first local computed from the element, when nothing after it reads the
 * element, is a map; what is left is the forEach. When what is left only adds to a list declared
 * empty just before the loop, the pipeline collects into it instead (addAll is a flatMap): toList()
 * when the list is only read afterwards and holds the stream's type, else
 * collect(Collectors.toList()) (the user's choice, 2026-11-10).
 */
final class LoopToStream {

  private LoopToStream() {}

  /** A stage of the pipeline: its method, and its lambda's parameter and expression. */
  private record Stage(String method, String param, String body) {
    String text() {
      return "." + method + "(" + param + " -> " + body + ")";
    }
  }

  /** The longest line the pipeline is written on before each stage goes on a line of its own. */
  private static final int WIDTH = 100;

  /**
   * Methods that change a collection or a map: called in the loop on its source, or on the map
   * whose entries, keys or values it walks, they are refused.
   */
  static final Set<String> CHANGES =
      Set.of(
          "add",
          "addAll",
          "remove",
          "removeAll",
          "removeIf",
          "retainAll",
          "clear",
          "set",
          "sort",
          "replaceAll",
          "addFirst",
          "addLast",
          "removeFirst",
          "removeLast",
          "put",
          "putAll",
          "putIfAbsent",
          "compute",
          "computeIfAbsent",
          "computeIfPresent",
          "merge");

  /** Methods of a list that only read it: the uses after the loop that keep toList() possible. */
  private static final Set<String> READS =
      Set.of(
          "get",
          "size",
          "isEmpty",
          "contains",
          "containsAll",
          "indexOf",
          "lastIndexOf",
          "stream",
          "forEach",
          "iterator",
          "equals",
          "hashCode",
          "toString",
          "toArray",
          "containsKey",
          "containsValue",
          "getOrDefault");

  static Outcome run(SourceProject project, String rel, Integer line) throws Exception {
    Precondition.require(
        Precondition.that(
            () -> line != null,
            () -> "loop_to_stream needs line, the 1-based line the loop starts on."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    Statement found =
        opened.startingOn(
            line,
            Statement.class,
            s ->
                s instanceof EnhancedForStatement
                    || s instanceof ForStatement
                    || s instanceof WhileStatement
                    || s instanceof DoStatement,
            "loop",
            "",
            "the loop");
    if (!(found instanceof EnhancedForStatement loop)) {
      throw new Refusal(
          "the loop at line "
              + line
              + " is not a for (T x : source) over a collection, which loop_to_stream takes."
              + " Rewrite it as one first: convert_loop with to=for_each takes an index for or a"
              + " for over an iterator.");
    }
    Precondition.require(
        Precondition.that(
            () -> !(loop.getParent() instanceof LabeledStatement),
            () -> "the loop at line " + line + " has a label, which a stream cannot keep."));
    Expression source = loop.getExpression();
    String from = source(text, source);
    ITypeBinding sourceType = source.resolveTypeBinding();
    if (sourceType == null || sourceType.isRecovered()) {
      throw new Refusal("the type of " + from + " does not resolve. Fix the compile errors first.");
    }
    if (sourceType.isArray()) {
      throw new Refusal(
          "the loop at line "
              + line
              + " walks an array, "
              + from
              + ", and loop_to_stream takes a collection for now.");
    }
    ITypeBinding element = elementOf(sourceType);
    if (element == null) {
      throw new Refusal(
          from + " is not an Iterable with a type argument, so its elements' type is not known.");
    }
    SingleVariableDeclaration variable = loop.getParameter();
    ITypeBinding declared = variable.getType().resolveBinding();
    if (declared == null || !declared.isEqualTo(element)) {
      throw new Refusal(
          "the loop declares "
              + variable.getName()
              + " as "
              + (declared == null ? "?" : declared.getName())
              + ", but the elements of "
              + from
              + " are "
              + element.getName()
              + ", and a lambda's parameter takes the elements' type, which can choose another"
              + " overload or unbox. Declare it as "
              + element.getName()
              + " first.");
    }
    Statement body = loop.getBody();
    VariableDeclarationStatement result = resultBefore(loop);
    IBinding computes = result == null ? null : fragment(result).resolveBinding();
    Read read = stages(text, variable, element, body);
    checkBody(opened, text, loop, body, computes, computes != null && searches(read, computes));
    String indent = Lines.indentOf(text, loop.getStartPosition());
    int step = Declarations.step(text) > 0 ? Declarations.step(text) : 4;
    VariableDeclarationStatement list = collected(text, loop, read.rest());
    VariableDeclarationStatement groups = grouping(loop, read.rest());
    Rewrite rewrite =
        result != null
            ? computed(text, loop, result, read, indent, step)
            : groups != null
                ? grouped(project, text, loop, groups, read, indent, step)
                : list != null
                    ? collect(text, loop, list, read, indent, step)
                    : forEach(text, loop, read, indent, step);
    String updated =
        text.substring(0, rewrite.start()) + rewrite.text() + text.substring(Nodes.end(loop));
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            updated,
            rewrite.imports(),
            "replacing the loop at line " + line + " with a stream",
            "A local the body reads must be effectively final for a lambda to read it.");
    return new Outcome(
        "replaced the loop at line "
            + line
            + " of "
            + rel
            + " with a stream of "
            + from
            + ": "
            + rewrite.said()
            + written.fixed().said(),
        written.declared());
  }

  /**
   * Refuses a body a lambda cannot hold, or whose stream would run differently: an early exit, an
   * assignment to a local declared outside it, a checked exception, a change to the collection it
   * walks, a text block (which the reindentation would change).
   */
  private static void checkBody(
      Opened opened,
      String text,
      EnhancedForStatement loop,
      Statement body,
      IBinding result,
      boolean search) {
    int line = Lines.number(text, loop.getStartPosition());
    if (!search && Effects.leaves(body, false)) {
      throw new Refusal(
          "the body of the loop at line "
              + line
              + " has a break, continue or return that leaves it, which a lambda cannot do.");
    }
    for (Effects.Effect e : Effects.of(body).all()) {
      if (e.kind() == Effects.Kind.WRITE
          && e.variable() != null
          && !e.variable().isField()
          && !declaredIn(opened, e.variable(), body)
          && !same(e.variable(), loop.getParameter().resolveBinding())
          && !same(e.variable(), result)) {
        throw new Refusal(
            e.variable().getName()
                + " is assigned in the loop at line "
                + line
                + ", and a lambda cannot assign a local of the method around it. Sums, counts and"
                + " other results are not built yet.");
      }
    }
    String thrown = checkedThrown(body);
    if (thrown != null) {
      throw new Refusal(
          "the body of the loop at line "
              + line
              + " may throw "
              + thrown
              + ", a checked exception, which a lambda passed to forEach, filter or map cannot"
              + " throw.");
    }
    Expression owner = owner(loop.getExpression());
    for (MethodInvocation m : Nodes.all(body, MethodInvocation.class)) {
      if (m.getExpression() == null || !CHANGES.contains(m.getName().getIdentifier())) {
        continue;
      }
      String receiver = source(text, m.getExpression());
      if (sameVariable(text, m.getExpression(), loop.getExpression())
          || (owner != null && sameVariable(text, m.getExpression(), owner))) {
        throw new Refusal(
            "the loop at line "
                + line
                + " changes "
                + receiver
                + ", which it walks ("
                + source(text, m)
                + "); a stream of it would fail at another point, or not at all.");
      }
    }
    if (!Nodes.all(body, TextBlock.class).isEmpty()) {
      throw new Refusal(
          "the body of the loop at line "
              + line
              + " holds a text block, which is not handled yet.");
    }
  }

  /**
   * The statement just before {@code loop} when the loop only collects into it: {@code List<R> out
   * = new ArrayList<>()}, typed List or Collection, and {@code rest} is one statement whose every
   * path ends in one {@code out.add(...)} or {@code out.addAll(...)} of a collection, or adds
   * nothing ({@link #branch}), and those are the loop's only uses of {@code out}; else null.
   */
  private static VariableDeclarationStatement collected(
      String text, EnhancedForStatement loop, List<Statement> rest) {
    if (!(loop.getParent() instanceof Block block)) {
      return null;
    }
    int at = block.statements().indexOf(loop);
    if (at < 1
        || !(block.statements().get(at - 1) instanceof VariableDeclarationStatement list)
        || list.fragments().size() != 1
        || !(list.fragments().get(0) instanceof VariableDeclarationFragment out)
        || !(out.getInitializer() instanceof ClassInstanceCreation made)
        || made.getAnonymousClassDeclaration() != null
        || !made.arguments().isEmpty()
        || made.resolveTypeBinding() == null
        || !made.resolveTypeBinding().getErasure().getQualifiedName().equals("java.util.ArrayList")
        || list.getType().resolveBinding() == null
        || !Set.of("java.util.List", "java.util.Collection")
            .contains(list.getType().resolveBinding().getErasure().getQualifiedName())) {
      return null;
    }
    if (rest.size() != 1) {
      return null;
    }
    Branch b = branch(text, rest.get(0), out.resolveBinding(), rest.get(0) instanceof IfStatement);
    if (b == null || b.adds() == 0) {
      return null;
    }
    long uses =
        Nodes.all(loop, SimpleName.class).stream()
            .filter(n -> same(n.resolveBinding(), out.resolveBinding()))
            .count();
    return uses == b.adds() ? list : null;
  }

  /**
   * Whether every use of {@code list} in {@code scope} after {@code from} only reads it: a call of
   * a reading method on it, or the collection of an enhanced for.
   */
  private static boolean onlyRead(IVariableBinding list, ASTNode scope, int from) {
    for (SimpleName n : Nodes.all(scope, SimpleName.class)) {
      if (n.getStartPosition() < from || !same(n.resolveBinding(), list)) {
        continue;
      }
      boolean read =
          (n.getParent() instanceof MethodInvocation m
                  && m.getExpression() == n
                  && READS.contains(m.getName().getIdentifier()))
              || (n.getParent() instanceof EnhancedForStatement f && f.getExpression() == n);
      if (!read) {
        return false;
      }
    }
    return true;
  }

  /** The first checked exception {@code body} may throw that it does not catch, or null. */
  private static String checkedThrown(Statement body) {
    for (ASTNode n : Nodes.all(body, ASTNode.class)) {
      if (Effects.later(n, body) != null) {
        continue;
      }
      List<ITypeBinding> thrown = new ArrayList<>();
      IMethodBinding m =
          n instanceof MethodInvocation i
              ? i.resolveMethodBinding()
              : n instanceof SuperMethodInvocation s
                  ? s.resolveMethodBinding()
                  : n instanceof ClassInstanceCreation c ? c.resolveConstructorBinding() : null;
      if (m != null) {
        thrown.addAll(List.of(m.getExceptionTypes()));
      }
      if (n instanceof ThrowStatement t && t.getExpression().resolveTypeBinding() != null) {
        thrown.add(t.getExpression().resolveTypeBinding());
      }
      for (ITypeBinding t : thrown) {
        if (checked(t) && !caught(n, t, body)) {
          return t.getName();
        }
      }
    }
    return null;
  }

  private static boolean checked(ITypeBinding t) {
    for (ITypeBinding c = t; c != null; c = c.getSuperclass()) {
      String name = c.getErasure().getQualifiedName();
      if (name.equals("java.lang.RuntimeException") || name.equals("java.lang.Error")) {
        return false;
      }
    }
    return true;
  }

  /** Whether a try inside {@code body} around {@code n} catches {@code t}. */
  private static boolean caught(ASTNode n, ITypeBinding t, Statement body) {
    for (ASTNode p = n.getParent(); p != null && p != body.getParent(); p = p.getParent()) {
      if (p instanceof TryStatement tr && Nodes.within(n, tr.getBody())) {
        for (Object o : tr.catchClauses()) {
          Type caughtType = ((CatchClause) o).getException().getType();
          List<?> types = caughtType instanceof UnionType u ? u.types() : List.of(caughtType);
          for (Object each : types) {
            Type c = (Type) each;
            if (c.resolveBinding() != null && t.isSubTypeCompatible(c.resolveBinding())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * The type argument of the {@code Iterable} that {@code t} is, a wildcard as its bound; else
   * null.
   */
  static ITypeBinding elementOf(ITypeBinding t) {
    if (t == null) {
      return null;
    }
    if (t.getErasure().getQualifiedName().equals("java.lang.Iterable")) {
      ITypeBinding[] arguments = t.getTypeArguments();
      if (arguments.length != 1) {
        return null;
      }
      ITypeBinding a = arguments[0];
      if (a.isCapture()) {
        a = a.getWildcard();
      }
      if (a.isWildcardType()) {
        return a.isUpperbound() ? a.getBound() : null;
      }
      return a;
    }
    for (ITypeBinding i : t.getInterfaces()) {
      ITypeBinding found = elementOf(i);
      if (found != null) {
        return found;
      }
    }
    return elementOf(t.getSuperclass());
  }

  /** Whether {@code t} is a {@code java.util.Collection}, which has {@code stream()}. */
  static boolean collection(ITypeBinding t) {
    if (t == null) {
      return false;
    }
    if (t.getErasure().getQualifiedName().equals("java.util.Collection")) {
      return true;
    }
    for (ITypeBinding i : t.getInterfaces()) {
      if (collection(i)) {
        return true;
      }
    }
    return collection(t.getSuperclass());
  }

  private static boolean declaredIn(Opened opened, IVariableBinding v, Statement body) {
    ASTNode d = opened.unit().findDeclaringNode(v.getVariableDeclaration());
    return d != null && Nodes.within(d, body);
  }

  private static boolean reads(List<Statement> statements, IBinding variable) {
    for (Statement s : statements) {
      for (SimpleName n : Nodes.all(s, SimpleName.class)) {
        if (same(n.resolveBinding(), variable)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean same(IBinding a, IBinding b) {
    return a != null && b != null && a.getKey().equals(b.getKey());
  }

  private static List<Statement> statementsOf(Statement s) {
    List<Statement> out = new ArrayList<>();
    if (s instanceof Block b) {
      for (Object o : b.statements()) {
        out.add((Statement) o);
      }
    } else {
      out.add(s);
    }
    return out;
  }

  /**
   * The lines of {@code rest}: the inside of {@code holder} when they are all of a block (its
   * comments too), else from the first's line to the last's.
   */
  private static String statementsText(String text, Statement holder, List<Statement> rest) {
    int[] range = statementsRange(text, holder, rest);
    return text.substring(range[0], range[1]);
  }

  /** Where {@link #statementsText} starts and ends in {@code text}. */
  private static int[] statementsRange(String text, Statement holder, List<Statement> rest) {
    if (holder instanceof Block b && b.statements().equals(rest)) {
      return new int[] {Lines.end(text, b.getStartPosition()), Lines.start(text, Nodes.end(b) - 1)};
    }
    return new int[] {
      Lines.start(text, rest.get(0).getStartPosition()),
      Lines.end(text, Nodes.end(rest.get(rest.size() - 1)))
    };
  }

  /** {@code lines} with their common indentation replaced by {@code to}. */
  static String reindent(String lines, String to) {
    String[] each = lines.split("\n", -1);
    int common = Integer.MAX_VALUE;
    for (String l : each) {
      if (!l.isBlank()) {
        common = Math.min(common, l.length() - l.stripLeading().length());
      }
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < each.length; i++) {
      String l = each[i];
      out.append(l.isBlank() ? "" : to + l.substring(common));
      if (i < each.length - 1) {
        out.append('\n');
      }
    }
    return out.toString();
  }

  /** The segments on one line after {@code prefix}, or one a line when that is too long. */
  private static String layout(String indent, String prefix, List<String> segments, int step) {
    String one = String.join("", segments);
    if (indent.length() + prefix.length() + one.length() + 1 <= WIDTH) {
      return one;
    }
    return String.join("\n" + indent + " ".repeat(2 * step), segments);
  }

  private static String summary(List<Stage> stages, String terminal) {
    List<String> names = new ArrayList<>();
    stages.forEach(s -> names.add(s.method()));
    names.add(terminal.substring(1));
    return String.join(", ", names);
  }

  /** {@code e}'s source, in parentheses unless a method can be called on it as it is. */
  static String primary(String text, Expression e) {
    String s = source(text, e);
    return e instanceof Name
            || e instanceof MethodInvocation
            || e instanceof FieldAccess
            || e instanceof ParenthesizedExpression
            || e instanceof ClassInstanceCreation
            || e instanceof ThisExpression
            || e instanceof ArrayAccess
        ? s
        : "(" + s + ")";
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), Nodes.end(node));
  }

  /**
   * What one path of a collecting body adds, as a stream: {@code Stream.of(e)} for {@code
   * out.add(e)}, {@code c.stream()} for {@code out.addAll(c)}, {@code Stream.empty()} for an empty
   * block, {@code cond ? a : b} for an if with an else. {@code element} is the type every path
   * adds, null when none does or they differ ({@code mixed}); {@code adds} counts the calls.
   */
  private record Branch(String stream, ITypeBinding element, boolean mixed, int adds) {}

  /**
   * {@code s} as a {@link Branch} of adds to {@code out}, or null when some path does anything
   * else. {@code nested} is false for the body's one statement, which may add an array (a map
   * writes it); under an if, {@code Stream.of} would spread one, so it is refused.
   */
  private static Branch branch(String text, Statement s, IBinding out, boolean nested) {
    if (s instanceof Block b) {
      if (b.statements().isEmpty()) {
        return new Branch("Stream.empty()", null, false, 0);
      }
      return b.statements().size() == 1
          ? branch(text, (Statement) b.statements().get(0), out, nested)
          : null;
    }
    if (s instanceof IfStatement ifs) {
      if (ifs.getElseStatement() == null) {
        return null;
      }
      Branch then = branch(text, ifs.getThenStatement(), out, true);
      Branch other = branch(text, ifs.getElseStatement(), out, true);
      if (then == null || other == null) {
        return null;
      }
      boolean mixed =
          then.mixed()
              || other.mixed()
              || (then.element() != null
                  && other.element() != null
                  && !then.element().isEqualTo(other.element()));
      boolean thenIsIf =
          ifs.getThenStatement() instanceof IfStatement
              || (ifs.getThenStatement() instanceof Block tb
                  && tb.statements().size() == 1
                  && tb.statements().get(0) instanceof IfStatement);
      return new Branch(
          source(text, ifs.getExpression())
              + " ? "
              + (thenIsIf ? "(" + then.stream() + ")" : then.stream())
              + " : "
              + other.stream(),
          mixed ? null : then.element() != null ? then.element() : other.element(),
          mixed,
          then.adds() + other.adds());
    }
    if (!(s instanceof ExpressionStatement e)
        || !(e.getExpression() instanceof MethodInvocation add)
        || !(add.getExpression() instanceof SimpleName receiver)
        || !same(receiver.resolveBinding(), out)
        || add.arguments().size() != 1) {
      return null;
    }
    Expression argument = (Expression) add.arguments().get(0);
    ITypeBinding type = argument.resolveTypeBinding();
    if (type == null) {
      return null;
    }
    return switch (add.getName().getIdentifier()) {
      case "add" ->
          nested && type.isArray()
              ? null
              : new Branch("Stream.of(" + source(text, argument) + ")", type, false, 1);
      case "addAll" ->
          collection(type)
              ? new Branch(primary(text, argument) + ".stream()", elementOf(type), false, 1)
              : null;
      default -> null;
    };
  }

  /**
   * The body read into stages: the stages, the element's name, binding and type after the last of
   * them, and the statements left, with the statement that holds them.
   */
  private record Read(
      List<Stage> stages,
      String param,
      IBinding binding,
      ITypeBinding type,
      Statement holder,
      List<Statement> rest) {}

  /**
   * What replaces the code from {@code start} to the loop's end, the imports it needs, and what it
   * is.
   */
  private record Rewrite(String text, int start, List<String> imports, String said) {}

  /**
   * Reads {@code body} into stages, from the outside in: an if with no else that holds the rest is
   * a filter; a first local computed from the element, when nothing after it reads the element, a
   * map.
   */
  private static Read stages(
      String text, SingleVariableDeclaration variable, ITypeBinding element, Statement body) {
    List<Stage> stages = new ArrayList<>();
    String param = variable.getName().getIdentifier();
    IBinding paramBinding = variable.resolveBinding();
    ITypeBinding current = element;
    Statement holder = body;
    List<Statement> rest = statementsOf(body);
    while (true) {
      if (rest.size() == 1
          && rest.get(0) instanceof IfStatement ifs
          && ifs.getElseStatement() == null) {
        stages.add(new Stage("filter", param, source(text, ifs.getExpression())));
        holder = ifs.getThenStatement();
        rest = statementsOf(holder);
        continue;
      }
      if (rest.size() >= 2
          && rest.get(0) instanceof VariableDeclarationStatement d
          && d.fragments().size() == 1
          && d.fragments().get(0) instanceof VariableDeclarationFragment f
          && f.getInitializer() != null
          && f.getExtraDimensions() == 0) {
        ITypeBinding type = d.getType().resolveBinding();
        ITypeBinding initial = f.getInitializer().resolveTypeBinding();
        List<Statement> after = rest.subList(1, rest.size());
        if (type != null
            && !type.isPrimitive()
            && initial != null
            && type.isEqualTo(initial)
            && !reads(after, paramBinding)) {
          stages.add(new Stage("map", param, source(text, f.getInitializer())));
          param = f.getName().getIdentifier();
          paramBinding = f.resolveBinding();
          current = type;
          rest = after;
          continue;
        }
      }
      return new Read(stages, param, paramBinding, current, holder, rest);
    }
  }

  /** The pipeline that collects into {@code list}, declared just before the loop. */
  private static Rewrite collect(
      String text,
      EnhancedForStatement loop,
      VariableDeclarationStatement list,
      Read read,
      String indent,
      int step) {
    List<Stage> stages = new ArrayList<>(read.stages());
    String param = read.param();
    List<Statement> rest = read.rest();
    VariableDeclarationFragment out = (VariableDeclarationFragment) list.fragments().get(0);
    ITypeBinding last;
    if (rest.get(0) instanceof IfStatement ifs) {
      Branch b = branch(text, ifs, out.resolveBinding(), true);
      stages.add(new Stage("flatMap", param, b.stream()));
      last = b.element();
    } else {
      MethodInvocation add = (MethodInvocation) ((ExpressionStatement) rest.get(0)).getExpression();
      Expression argument = (Expression) add.arguments().get(0);
      if (add.getName().getIdentifier().equals("addAll")) {
        stages.add(new Stage("flatMap", param, primary(text, argument) + ".stream()"));
        last = elementOf(argument.resolveTypeBinding());
      } else if (argument instanceof SimpleName n && same(n.resolveBinding(), read.binding())) {
        last = read.type();
      } else {
        stages.add(new Stage("map", param, source(text, argument)));
        last = argument.resolveTypeBinding();
      }
    }
    ITypeBinding[] held = list.getType().resolveBinding().getTypeArguments();
    boolean toList =
        held.length == 1
            && last != null
            && last.isEqualTo(held[0])
            && onlyRead(out.resolveBinding(), list.getParent(), Nodes.end(loop));
    String terminal = toList ? ".toList()" : ".collect(Collectors.toList())";
    List<String> imports = new ArrayList<>();
    if (!toList) {
      imports.add("java.util.stream.Collectors");
    }
    if (stages.stream().anyMatch(s -> s.body().contains("Stream."))) {
      imports.add("java.util.stream.Stream");
    }
    String prefix =
        source(text, list)
            .substring(0, out.getInitializer().getStartPosition() - list.getStartPosition());
    List<String> segments = new ArrayList<>();
    segments.add(primary(text, loop.getExpression()) + ".stream()");
    stages.forEach(s -> segments.add(s.text()));
    segments.add(terminal);
    return new Rewrite(
        lead(text, list, loop, indent) + prefix + layout(indent, prefix, segments, step) + ";",
        list.getStartPosition(),
        imports,
        "collected into " + out.getName() + " by " + summary(stages, terminal));
  }

  /** The pipeline whose last stage runs what is left of the body in a forEach. */
  private static Rewrite forEach(
      String text, EnhancedForStatement loop, Read read, String indent, int step) {
    if (read.stages().isEmpty()) {
      Rewrite entries = entries(text, loop, read, indent, step);
      if (entries != null) {
        return entries;
      }
    }
    List<String> segments = new ArrayList<>();
    String receiver = primary(text, loop.getExpression());
    if (read.stages().isEmpty()) {
      segments.add(receiver);
    } else {
      segments.add(receiver + ".stream()");
      read.stages().forEach(s -> segments.add(s.text()));
    }
    List<Statement> rest = read.rest();
    String lambda = read.param() + " -> ";
    String head = String.join("", segments) + ".forEach(" + lambda;
    boolean wrap = indent.length() + head.length() + 2 > WIDTH;
    String callIndent = wrap ? indent + " ".repeat(2 * step) : indent;
    String forEach;
    if (rest.size() == 1 && rest.get(0) instanceof ExpressionStatement e) {
      forEach = ".forEach(" + lambda + source(text, e.getExpression()) + ")";
    } else {
      forEach =
          ".forEach("
              + lambda
              + "{\n"
              + reindent(statementsText(text, read.holder(), rest), callIndent + " ".repeat(step))
              + callIndent
              + "})";
    }
    segments.add(forEach);
    return new Rewrite(
        (wrap ? String.join("\n" + callIndent, segments) : String.join("", segments)) + ";",
        loop.getStartPosition(),
        List.of(),
        read.stages().isEmpty() ? "forEach" : summary(read.stages(), ".forEach"));
  }

  /**
   * The local declared just before {@code loop}, with an initializer, that the loop assigns: what a
   * loop computes, such as a flag; else null.
   */
  private static VariableDeclarationStatement resultBefore(EnhancedForStatement loop) {
    if (!(loop.getParent() instanceof Block block)) {
      return null;
    }
    int at = block.statements().indexOf(loop);
    if (at < 1
        || !(block.statements().get(at - 1) instanceof VariableDeclarationStatement d)
        || d.fragments().size() != 1
        || fragment(d).getInitializer() == null) {
      return null;
    }
    IBinding v = fragment(d).resolveBinding();
    for (Effects.Effect e : Effects.of(loop.getBody()).all()) {
      if (e.kind() == Effects.Kind.WRITE && same(e.variable(), v)) {
        return d;
      }
    }
    return null;
  }

  private static VariableDeclarationFragment fragment(VariableDeclarationStatement d) {
    return (VariableDeclarationFragment) d.fragments().get(0);
  }

  /**
   * The pipeline that computes {@code result}: a sum or a count ({@link #total}), or a flag, which
   * starts as a constant, and which the loop only sets to one other constant. Each element maps to
   * whether its pass set it, and the passes are or-ed by {@code reduce}, which runs every element,
   * as the loop did ({@code anyMatch} would stop at the first). A last filter whose then-branch
   * only sets the flag maps to its condition.
   */
  private static Rewrite computed(
      String text,
      EnhancedForStatement loop,
      VariableDeclarationStatement result,
      Read read,
      String indent,
      int step) {
    VariableDeclarationFragment v = fragment(result);
    IBinding binding = v.resolveBinding();
    String initial = source(text, v.getInitializer());
    int line = Lines.number(text, loop.getStartPosition());
    List<Assignment> sets = new ArrayList<>();
    for (Assignment a : Nodes.all(loop.getBody(), Assignment.class)) {
      if (a.getLeftHandSide() instanceof SimpleName n && same(n.resolveBinding(), binding)) {
        sets.add(a);
      }
    }
    long uses =
        Nodes.all(loop.getBody(), SimpleName.class).stream()
            .filter(n -> same(n.resolveBinding(), binding))
            .count();
    String set = sets.isEmpty() ? null : source(text, sets.get(0).getRightHandSide());
    boolean flag =
        constant(v.getInitializer())
            && !sets.isEmpty()
            && uses == sets.size()
            && sets.stream()
                .allMatch(a ->
                    a.getOperator() == Assignment.Operator.ASSIGN
                        && constant(a.getRightHandSide())
                        && source(text, a.getRightHandSide()).equals(set))
            && !set.equals(initial);
    if (!flag) {
      Rewrite total = total(text, loop, result, read, indent, step);
      if (total != null) {
        return total;
      }
      throw new Refusal(
          v.getName()
              + " is assigned in the loop at line "
              + line
              + ", and a lambda cannot assign a local of the method around it. loop_to_stream"
              + " takes a flag (a local set to one constant from another), an int or long sum"
              + " (+=) or a count (++); not this.");
    }
    if (searches(read, binding)) {
      return search(text, loop, result, read, set, indent, step);
    }
    List<Stage> stages = new ArrayList<>(read.stages());
    List<Statement> rest = read.rest();
    String cont = indent + " ".repeat(2 * step);
    List<String> segments = new ArrayList<>();
    segments.add(primary(text, loop.getExpression()) + ".stream()");
    if (rest.size() == 1
        && rest.get(0) instanceof ExpressionStatement e
        && sets.contains(e.getExpression())
        && !stages.isEmpty()
        && stages.getLast().method().equals("filter")) {
      Stage last = stages.removeLast();
      stages.add(new Stage("map", last.param(), last.body()));
      stages.forEach(s -> segments.add(s.text()));
    } else {
      String name = Names.fresh(v.getName() + "Changed", loop);
      int[] range = statementsRange(text, read.holder(), rest);
      List<TextEdit> edits = new ArrayList<>();
      for (Assignment a : sets) {
        edits.add(new TextEdit(a.getStartPosition(), Nodes.end(a), name + " = true"));
      }
      String inner = cont + " ".repeat(step);
      String lines =
          reindent(TextEdit.apply(text.substring(range[0], range[1]), edits, range[0]), inner);
      stages.forEach(s -> segments.add(s.text()));
      segments.add(
          ".map("
              + read.param()
              + " -> {\n"
              + inner
              + "boolean "
              + name
              + " = false;\n"
              + lines
              + inner
              + "return "
              + name
              + ";\n"
              + cont
              + "})");
    }
    String tail;
    String head = "";
    if (initial.equals("false") && set.equals("true")) {
      tail = "";
    } else if (initial.equals("true") && set.equals("false")) {
      head = "!";
      tail = "";
    } else {
      tail = " ? " + set + " : " + initial;
    }
    segments.add(".reduce(false, Boolean::logicalOr)" + tail);
    String prefix =
        source(text, result)
            .substring(0, v.getInitializer().getStartPosition() - result.getStartPosition());
    String one = String.join("", segments);
    boolean wrap =
        one.contains("\n")
            || indent.length() + prefix.length() + head.length() + one.length() + 1 > WIDTH;
    String pipeline = wrap ? String.join("\n" + cont, segments) : one;
    return new Rewrite(
        lead(text, result, loop, indent) + prefix + head + pipeline + ";",
        result.getStartPosition(),
        List.of(),
        "set " + v.getName() + " by " + summary(stages, ".reduce"));
  }

  /**
   * The comment lines between {@code before} and {@code loop}, which a rewrite that replaces both
   * keeps above them, then the indentation the replacement's first line needs; empty when there are
   * none.
   */
  private static String lead(String text, Statement before, Statement loop, String indent) {
    String between =
        text.substring(
            Lines.end(text, Nodes.end(before)), Lines.start(text, loop.getStartPosition()));
    return between.isBlank() ? "" : between.stripLeading() + indent;
  }

  /** Whether {@code e} is a literal: a number, a boolean, a character or a string. */
  private static boolean constant(Expression e) {
    return e instanceof NumberLiteral
        || e instanceof BooleanLiteral
        || e instanceof CharacterLiteral
        || e instanceof StringLiteral
        || (e instanceof PrefixExpression p
            && p.getOperator() == PrefixExpression.Operator.MINUS
            && p.getOperand() instanceof NumberLiteral);
  }

  /**
   * The pipeline for a sum or a count: {@code result}, an int or a long, takes one {@code +=} of a
   * value per element, or one {@code ++} (a count), and nothing else in the loop reads it. {@code
   * mapToInt(...).sum()} wraps on overflow as {@code +=} does, and a count's long cast to an int
   * keeps the same low bits. Null for anything else; a float or double sum is refused, since a
   * stream may add in another order and round differently (roadmap #135).
   */
  private static Rewrite total(
      String text,
      EnhancedForStatement loop,
      VariableDeclarationStatement result,
      Read read,
      String indent,
      int step) {
    VariableDeclarationFragment v = fragment(result);
    IBinding binding = v.resolveBinding();
    List<Statement> rest = read.rest();
    long uses =
        Nodes.all(loop.getBody(), SimpleName.class).stream()
            .filter(n -> same(n.resolveBinding(), binding))
            .count();
    if (rest.size() != 1 || !(rest.get(0) instanceof ExpressionStatement e) || uses != 1) {
      return null;
    }
    Expression update = e.getExpression();
    Expression added = null;
    if (update instanceof Assignment a
        && a.getOperator() == Assignment.Operator.PLUS_ASSIGN
        && a.getLeftHandSide() instanceof SimpleName n
        && same(n.resolveBinding(), binding)) {
      added = a.getRightHandSide();
    } else if (!(update instanceof PostfixExpression p
            && p.getOperator() == PostfixExpression.Operator.INCREMENT
            && p.getOperand() instanceof SimpleName n2
            && same(n2.resolveBinding(), binding))
        && !(update instanceof PrefixExpression q
            && q.getOperator() == PrefixExpression.Operator.INCREMENT
            && q.getOperand() instanceof SimpleName n3
            && same(n3.resolveBinding(), binding))) {
      return null;
    }
    boolean count = added == null || source(text, added).equals("1");
    ITypeBinding type = result.getType().resolveBinding();
    String kind = type == null ? "" : type.getName();
    if (kind.equals("double") || kind.equals("float")) {
      throw new Refusal(
          v.getName()
              + " is a floating-point sum, which a stream may add in another order and round"
              + " differently; loop_to_stream refuses it for now (roadmap #135).");
    }
    if (!kind.equals("int") && !kind.equals("long")) {
      return null;
    }
    if (!count) {
      ITypeBinding addedType = added.resolveTypeBinding();
      Set<String> fits =
          kind.equals("int")
              ? Set.of("int", "short", "byte", "char", "Integer", "Short", "Byte", "Character")
              : Set.of(
                  "int",
                  "short",
                  "byte",
                  "char",
                  "long",
                  "Integer",
                  "Short",
                  "Byte",
                  "Character",
                  "Long");
      if (addedType == null || !fits.contains(addedType.getName())) {
        return null;
      }
    }
    List<String> segments = new ArrayList<>();
    segments.add(primary(text, loop.getExpression()) + ".stream()");
    read.stages().forEach(s -> segments.add(s.text()));
    String terminal;
    if (count) {
      terminal = ".count()";
    } else {
      segments.add(
          (kind.equals("int") ? ".mapToInt(" : ".mapToLong(")
              + read.param()
              + " -> "
              + source(text, added)
              + ")");
      terminal = ".sum()";
    }
    segments.add(terminal);
    Expression initial = v.getInitializer();
    String from = source(text, initial);
    String plus =
        from.equals("0") || from.equalsIgnoreCase("0L")
            ? ""
            : (initial instanceof Name || initial instanceof MethodInvocation || constant(initial)
                    ? from
                    : "(" + from + ")")
                + " + ";
    String cast = count && kind.equals("int") ? "(int) " : "";
    String prefix =
        source(text, result).substring(0, initial.getStartPosition() - result.getStartPosition());
    String before = prefix + plus + cast;
    return new Rewrite(
        lead(text, result, loop, indent) + before + layout(indent, before, segments, step) + ";",
        result.getStartPosition(),
        List.of(),
        (count ? "counted " : "summed ") + v.getName() + " by " + summary(read.stages(), terminal));
  }

  /**
   * Whether what is left of the body after its stages sets {@code result} and breaks out of the
   * loop: a search, which stops at the first element that passes the filters, as anyMatch does.
   */
  private static boolean searches(Read read, IBinding result) {
    List<Statement> rest = read.rest();
    return rest.size() == 2
        && rest.get(0) instanceof ExpressionStatement e
        && e.getExpression() instanceof Assignment a
        && a.getLeftHandSide() instanceof SimpleName n
        && same(n.resolveBinding(), result)
        && rest.get(1) instanceof BreakStatement b
        && b.getLabel() == null;
  }

  /**
   * The pipeline for a search: the last filter's condition becomes anyMatch, or, for a flag that
   * starts true and is set false, its negation becomes allMatch. Both stop at the first match, as
   * the loop's break did, so they run each stage as often as the loop ran it.
   */
  private static Rewrite search(
      String text,
      EnhancedForStatement loop,
      VariableDeclarationStatement result,
      Read read,
      String set,
      String indent,
      int step) {
    VariableDeclarationFragment v = fragment(result);
    String initial = source(text, v.getInitializer());
    List<Stage> stages = new ArrayList<>(read.stages());
    if (stages.isEmpty() || !stages.getLast().method().equals("filter")) {
      throw new Refusal(
          "the loop at line "
              + Lines.number(text, loop.getStartPosition())
              + " sets "
              + v.getName()
              + " and breaks on its first pass; loop_to_stream takes a break only under a"
              + " condition, as a search.");
    }
    Stage last = stages.removeLast();
    Expression condition = ((IfStatement) read.holder().getParent()).getExpression();
    boolean all = initial.equals("true") && set.equals("false");
    String test;
    if (all) {
      test =
          condition instanceof PrefixExpression p
                  && p.getOperator() == PrefixExpression.Operator.NOT
              ? source(text, p.getOperand())
              : "!(" + source(text, condition) + ")";
    } else {
      test = last.body();
    }
    List<String> segments = new ArrayList<>();
    segments.add(primary(text, loop.getExpression()) + ".stream()");
    stages.forEach(s -> segments.add(s.text()));
    String tail =
        all || (initial.equals("false") && set.equals("true")) ? "" : " ? " + set + " : " + initial;
    String terminal = all ? ".allMatch(" : ".anyMatch(";
    segments.add(terminal + last.param() + " -> " + test + ")" + tail);
    String prefix =
        source(text, result)
            .substring(0, v.getInitializer().getStartPosition() - result.getStartPosition());
    return new Rewrite(
        lead(text, result, loop, indent) + prefix + layout(indent, prefix, segments, step) + ";",
        result.getStartPosition(),
        List.of(),
        "found "
            + v.getName()
            + " by "
            + summary(stages, terminal.substring(0, terminal.length() - 1)));
  }

  /**
   * The map whose {@code entrySet()}, {@code keySet()} or {@code values()} {@code source} is; else
   * null.
   */
  private static Expression owner(Expression source) {
    return source instanceof MethodInvocation m
            && m.getExpression() != null
            && m.arguments().isEmpty()
            && Set.of("entrySet", "keySet", "values").contains(m.getName().getIdentifier())
        ? m.getExpression()
        : null;
  }

  /**
   * {@code map.forEach((key, value) -> ...)} for a loop over {@code map.entrySet()} that uses its
   * entry only as {@code e.getKey()} and {@code e.getValue()}; else null. A map's forEach visits
   * its entries in the order its entry set iterates them.
   */
  private static Rewrite entries(
      String text, EnhancedForStatement loop, Read read, String indent, int step) {
    if (!(loop.getExpression() instanceof MethodInvocation set)
        || !set.getName().getIdentifier().equals("entrySet")
        || set.getExpression() == null
        || !set.arguments().isEmpty()
        || !map(set.getExpression().resolveTypeBinding())) {
      return null;
    }
    IBinding entry = loop.getParameter().resolveBinding();
    String key = Names.fresh("key", loop);
    String value = Names.fresh("value", loop);
    List<TextEdit> edits = new ArrayList<>();
    for (SimpleName n : Nodes.all(loop.getBody(), SimpleName.class)) {
      if (!same(n.resolveBinding(), entry)) {
        continue;
      }
      if (!(n.getParent() instanceof MethodInvocation m)
          || m.getExpression() != n
          || !m.arguments().isEmpty()
          || !Set.of("getKey", "getValue").contains(m.getName().getIdentifier())) {
        return null;
      }
      edits.add(
          new TextEdit(
              m.getStartPosition(),
              Nodes.end(m),
              m.getName().getIdentifier().equals("getKey") ? key : value));
    }
    List<Statement> rest = read.rest();
    String head = primary(text, set.getExpression()) + ".forEach((" + key + ", " + value + ") -> ";
    String lambda;
    if (rest.size() == 1 && rest.get(0) instanceof ExpressionStatement e) {
      Expression x = e.getExpression();
      lambda = TextEdit.apply(source(text, x), edits, x.getStartPosition());
    } else {
      int[] range = statementsRange(text, read.holder(), rest);
      lambda =
          "{\n"
              + reindent(
                  TextEdit.apply(text.substring(range[0], range[1]), edits, range[0]),
                  indent + " ".repeat(step))
              + indent
              + "}";
    }
    return new Rewrite(
        head + lambda + ");", loop.getStartPosition(), List.of(), "forEach of the map's entries");
  }

  /** Whether {@code t} is a {@code java.util.Map}. */
  private static boolean map(ITypeBinding t) {
    if (t == null) {
      return false;
    }
    if (t.getErasure().getQualifiedName().equals("java.util.Map")) {
      return true;
    }
    for (ITypeBinding i : t.getInterfaces()) {
      if (map(i)) {
        return true;
      }
    }
    return map(t.getSuperclass());
  }

  /**
   * The statement just before {@code loop} when the loop only groups into it: {@code Map<K,
   * List<V>> m = new HashMap<>()}, and what is left of the body is one {@code
   * m.computeIfAbsent(key, k -> new ArrayList<>()).add(value)} whose lambda ignores its parameter,
   * the loop's only use of {@code m}; else null.
   */
  private static VariableDeclarationStatement grouping(
      EnhancedForStatement loop, List<Statement> rest) {
    if (!(loop.getParent() instanceof Block block)) {
      return null;
    }
    int at = block.statements().indexOf(loop);
    if (at < 1
        || !(block.statements().get(at - 1) instanceof VariableDeclarationStatement d)
        || d.fragments().size() != 1
        || !(fragment(d).getInitializer() instanceof ClassInstanceCreation made)
        || made.getAnonymousClassDeclaration() != null
        || !made.arguments().isEmpty()
        || made.resolveTypeBinding() == null
        || !made.resolveTypeBinding().getErasure().getQualifiedName().equals("java.util.HashMap")
        || d.getType().resolveBinding() == null
        || !d.getType().resolveBinding().getErasure().getQualifiedName().equals("java.util.Map")) {
      return null;
    }
    IBinding m = fragment(d).resolveBinding();
    MethodInvocation add = groupAdd(rest, m);
    long uses =
        Nodes.all(loop, SimpleName.class).stream().filter(n -> same(n.resolveBinding(), m)).count();
    return add != null && uses == 1 ? d : null;
  }

  /**
   * {@code rest}'s one {@code m.computeIfAbsent(key, k -> new ArrayList<>()).add(value)}, or null.
   */
  private static MethodInvocation groupAdd(List<Statement> rest, IBinding m) {
    if (rest.size() != 1
        || !(rest.get(0) instanceof ExpressionStatement e)
        || !(e.getExpression() instanceof MethodInvocation add)
        || !add.getName().getIdentifier().equals("add")
        || add.arguments().size() != 1
        || !(add.getExpression() instanceof MethodInvocation compute)
        || !compute.getName().getIdentifier().equals("computeIfAbsent")
        || !(compute.getExpression() instanceof SimpleName map)
        || !same(map.resolveBinding(), m)
        || compute.arguments().size() != 2
        || !(compute.arguments().get(1) instanceof LambdaExpression make)
        || make.parameters().size() != 1
        || !(make.getBody() instanceof ClassInstanceCreation list)
        || !list.arguments().isEmpty()
        || list.getAnonymousClassDeclaration() != null
        || list.resolveTypeBinding() == null
        || !list.resolveTypeBinding()
            .getErasure()
            .getQualifiedName()
            .equals("java.util.ArrayList")) {
      return null;
    }
    IBinding ignored = ((VariableDeclaration) make.parameters().get(0)).resolveBinding();
    boolean used =
        Nodes.all(make.getBody(), SimpleName.class).stream()
            .anyMatch(n -> same(n.resolveBinding(), ignored));
    return used ? null : add;
  }

  /**
   * The pipeline that groups into {@code groups}: {@code groupingBy} of the key, with {@code
   * mapping} when the value is not the element. Plain when the map is only read afterwards and its
   * types are the stream's; else with {@code HashMap::new} and {@code
   * toCollection(ArrayList::new)}, the types the loop made, since groupingBy's are not specified.
   * groupingBy throws on a null key, which computeIfAbsent grouped: that change is reported.
   */
  private static Rewrite grouped(
      SourceProject project,
      String text,
      EnhancedForStatement loop,
      VariableDeclarationStatement groups,
      Read read,
      String indent,
      int step) {
    VariableDeclarationFragment m = fragment(groups);
    MethodInvocation add = groupAdd(read.rest(), m.resolveBinding());
    MethodInvocation compute = (MethodInvocation) add.getExpression();
    Expression key = (Expression) compute.arguments().get(0);
    Expression value = (Expression) add.arguments().get(0);
    String param = read.param();
    boolean element = value instanceof SimpleName n && same(n.resolveBinding(), read.binding());
    ITypeBinding[] held = groups.getType().resolveBinding().getTypeArguments();
    ITypeBinding valueType = element ? read.type() : value.resolveTypeBinding();
    boolean plain =
        held.length == 2
            && key.resolveTypeBinding() != null
            && key.resolveTypeBinding().isEqualTo(held[0])
            && held[1].getTypeArguments().length == 1
            && valueType != null
            && valueType.isEqualTo(held[1].getTypeArguments()[0])
            && onlyRead(m.resolveBinding(), groups.getParent(), Nodes.end(loop));
    String classifier = param + " -> " + source(text, key);
    String downstream =
        element
            ? "Collectors.toCollection(ArrayList::new)"
            : "Collectors.mapping("
                + param
                + " -> "
                + source(text, value)
                + ", Collectors.toCollection(ArrayList::new))";
    if (plain && !element) {
      downstream =
          "Collectors.mapping(" + param + " -> " + source(text, value) + ", Collectors.toList())";
    }
    List<String> arguments =
        plain
            ? (element ? List.of(classifier) : List.of(classifier, downstream))
            : List.of(classifier, "HashMap::new", downstream);
    String cont = indent + " ".repeat(2 * step);
    String one = ".collect(Collectors.groupingBy(" + String.join(", ", arguments) + "))";
    String collect =
        cont.length() + one.length() + 1 <= WIDTH
            ? one
            : ".collect(Collectors.groupingBy(\n"
                + cont
                + " ".repeat(2 * step)
                + String.join(",\n" + cont + " ".repeat(2 * step), arguments)
                + "))";
    List<String> segments = new ArrayList<>();
    segments.add(primary(text, loop.getExpression()) + ".stream()");
    read.stages().forEach(s -> segments.add(s.text()));
    segments.add(collect);
    String prefix =
        source(text, groups)
            .substring(0, m.getInitializer().getStartPosition() - groups.getStartPosition());
    String joined = String.join("", segments);
    String pipeline =
        !joined.contains("\n") && indent.length() + prefix.length() + joined.length() + 1 <= WIDTH
            ? joined
            : String.join("\n" + cont, segments);
    Exceptional.change(
        project,
        "a null key from "
            + source(text, key)
            + " now throws a NullPointerException in groupingBy, where computeIfAbsent grouped it",
        "make sure no key is null, or keep the loop");
    List<String> imports = new ArrayList<>(List.of("java.util.stream.Collectors"));
    if (!plain) {
      imports.add("java.util.HashMap");
      imports.add("java.util.ArrayList");
    }
    return new Rewrite(
        lead(text, groups, loop, indent) + prefix + pipeline + ";",
        groups.getStartPosition(),
        imports,
        "grouped into " + m.getName() + " by " + summary(read.stages(), ".collect(groupingBy)"));
  }

  /**
   * Whether {@code a} and {@code b} are the same variable: by binding when each is a name or {@code
   * this.f} ({@code this.items} and {@code items}), else by their text.
   */
  static boolean sameVariable(String text, Expression a, Expression b) {
    IVariableBinding x = variable(a);
    IVariableBinding y = variable(b);
    if (x != null && y != null) {
      return x.getKey().equals(y.getKey());
    }
    return source(text, a).equals(source(text, b));
  }

  private static IVariableBinding variable(Expression e) {
    if (e instanceof SimpleName n && n.resolveBinding() instanceof IVariableBinding v) {
      return v;
    }
    if (e instanceof FieldAccess f
        && f.getExpression() instanceof ThisExpression t
        && t.getQualifier() == null) {
      return f.resolveFieldBinding();
    }
    return null;
  }
}
