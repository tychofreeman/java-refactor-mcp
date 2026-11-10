package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
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
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
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

  /** Methods that change a collection: called in the loop on its source, they are refused. */
  private static final Set<String> CHANGES =
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
          "removeLast");

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
          "toArray");

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
              + " Rewrite it as one first (convert_loop, which would, is not built yet).");
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
    checkBody(opened, text, loop, body, from);

    // Read the stages, from the outside in.
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
      break;
    }

    String indent = Lines.indentOf(text, loop.getStartPosition());
    int step = Declarations.step(text) > 0 ? Declarations.step(text) : 4;
    VariableDeclarationStatement list = collected(loop, rest, paramBinding);
    String replacement;
    int start;
    List<String> chosen = List.of();
    String said;
    if (list != null) {
      VariableDeclarationFragment out = (VariableDeclarationFragment) list.fragments().get(0);
      MethodInvocation add = (MethodInvocation) ((ExpressionStatement) rest.get(0)).getExpression();
      Expression argument = (Expression) add.arguments().get(0);
      ITypeBinding last;
      if (add.getName().getIdentifier().equals("addAll")) {
        stages.add(new Stage("flatMap", param, primary(text, argument) + ".stream()"));
        last = elementOf(argument.resolveTypeBinding());
      } else if (argument instanceof SimpleName n && same(n.resolveBinding(), paramBinding)) {
        last = current;
      } else {
        stages.add(new Stage("map", param, source(text, argument)));
        last = argument.resolveTypeBinding();
      }
      ITypeBinding[] held = list.getType().resolveBinding().getTypeArguments();
      boolean toList =
          held.length == 1
              && last != null
              && last.isEqualTo(held[0])
              && onlyRead(out.resolveBinding(), list.getParent(), Nodes.end(loop));
      String terminal = toList ? ".toList()" : ".collect(Collectors.toList())";
      if (!toList) {
        chosen = List.of("java.util.stream.Collectors");
      }
      String prefix =
          source(text, list)
              .substring(0, out.getInitializer().getStartPosition() - list.getStartPosition());
      List<String> segments = new ArrayList<>();
      segments.add(primary(text, source) + ".stream()");
      stages.forEach(s -> segments.add(s.text()));
      segments.add(terminal);
      replacement = prefix + layout(indent, prefix, segments, step) + ";";
      start = list.getStartPosition();
      said = "collected into " + out.getName() + " by " + summary(stages, terminal);
    } else {
      List<String> segments = new ArrayList<>();
      String receiver = primary(text, source);
      if (stages.isEmpty()) {
        segments.add(receiver);
      } else {
        segments.add(receiver + ".stream()");
        stages.forEach(s -> segments.add(s.text()));
      }
      String lambda = param + " -> ";
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
                + reindent(statementsText(text, holder, rest), callIndent + " ".repeat(step))
                + callIndent
                + "})";
      }
      segments.add(forEach);
      replacement =
          (wrap ? String.join("\n" + callIndent, segments) : String.join("", segments)) + ";";
      start = loop.getStartPosition();
      said = stages.isEmpty() ? "forEach" : summary(stages, ".forEach");
    }
    String updated = text.substring(0, start) + replacement + text.substring(Nodes.end(loop));
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            updated,
            chosen,
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
            + said
            + written.fixed().said(),
        written.declared());
  }

  /**
   * Refuses a body a lambda cannot hold, or whose stream would run differently: an early exit, an
   * assignment to a local declared outside it, a checked exception, a change to the collection it
   * walks, a text block (which the reindentation would change).
   */
  private static void checkBody(
      Opened opened, String text, EnhancedForStatement loop, Statement body, String from) {
    int line = Lines.number(text, loop.getStartPosition());
    if (Effects.leaves(body)) {
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
          && !same(e.variable(), loop.getParameter().resolveBinding())) {
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
    for (MethodInvocation m : Nodes.all(body, MethodInvocation.class)) {
      if (m.getExpression() != null
          && CHANGES.contains(m.getName().getIdentifier())
          && source(text, m.getExpression()).equals(from)) {
        throw new Refusal(
            "the loop at line "
                + line
                + " changes "
                + from
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
   * = new ArrayList<>()}, typed List or Collection, and {@code rest} is one {@code out.add(...)} or
   * {@code out.addAll(...)} of a collection, the loop's only use of {@code out}; else null.
   */
  private static VariableDeclarationStatement collected(
      EnhancedForStatement loop, List<Statement> rest, IBinding param) {
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
    if (rest.size() != 1
        || !(rest.get(0) instanceof ExpressionStatement e)
        || !(e.getExpression() instanceof MethodInvocation add)
        || !(add.getExpression() instanceof SimpleName receiver)
        || !same(receiver.resolveBinding(), out.resolveBinding())
        || add.arguments().size() != 1
        || !Set.of("add", "addAll").contains(add.getName().getIdentifier())) {
      return null;
    }
    if (add.getName().getIdentifier().equals("addAll")
        && !collection(((Expression) add.arguments().get(0)).resolveTypeBinding())) {
      return null;
    }
    long uses =
        Nodes.all(loop, SimpleName.class).stream()
            .filter(n -> same(n.resolveBinding(), out.resolveBinding()))
            .count();
    return uses == 1 ? list : null;
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
  private static ITypeBinding elementOf(ITypeBinding t) {
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
  private static boolean collection(ITypeBinding t) {
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
    if (holder instanceof Block b && b.statements().equals(rest)) {
      return text.substring(
          Lines.end(text, b.getStartPosition()), Lines.start(text, Nodes.end(b) - 1));
    }
    return text.substring(
        Lines.start(text, rest.get(0).getStartPosition()),
        Lines.end(text, Nodes.end(rest.get(rest.size() - 1))));
  }

  /** {@code lines} with their common indentation replaced by {@code to}. */
  private static String reindent(String lines, String to) {
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
  private static String primary(String text, Expression e) {
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
}
