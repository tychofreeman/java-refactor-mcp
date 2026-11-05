package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * fold: code that does what an existing static method does becomes a call of it. The method's body
 * is the pattern: one return's expression matches expressions, and a void method's statements match
 * runs of statements in any block. Its parameters stand for any expressions of their types, a local
 * it declares for a local the match declares, and every other name must mean what it means in the
 * body. The method stays. A match the call would not reproduce (its arguments run in another order
 * or another number of times, or moved past what the behavior gate refuses) is left, with why.
 */
final class Fold {

  private Fold() {}

  /** One piece of code that matched the body, and what a call of the method needs for it. */
  private record Candidate(
      Path file,
      CompilationUnit unit,
      ASTNode root,
      ASTNode landing,
      int start,
      int end,
      Expression[] args,
      Set<String> locals,
      Block block,
      int after) {}

  static Outcome run(SourceProject project, String rel, String name, Integer line, String in)
      throws Exception {
    int dot = name.lastIndexOf('.');
    Precondition.require(
        Precondition.that(
            () -> dot > 0,
            () -> "name the method as Type.method: " + name + " has no type before it."));
    String typeName = name.substring(name.lastIndexOf('.', dot - 1) + 1, dot);
    String methodName = name.substring(dot + 1);
    Path file = project.javaFile(rel);
    MethodDeclaration first = method(Opened.of(project, rel).unit(), typeName, methodName, name);
    Holding holding = Holding.of(project, anchor(first), file);
    MethodDeclaration m = method(holding.units().get(file), typeName, methodName, name);
    IMethodBinding mb = m.resolveBinding();
    Precondition.require(
        Precondition.that(
            () -> m.getBody() != null && Modifier.isStatic(m.getModifiers()) && mb != null,
            () ->
                name
                    + " is not a static method with a body; fold makes calls of a static method."
                    + " Make it static first (move_member to its own class)."));

    List<String> params = new ArrayList<>();
    for (Object o : m.parameters()) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) o;
      if (p.isVarargs()) {
        throw new Refusal(name + " takes varargs, which fold does not match. Use a list.");
      }
      params.add(key(p.resolveBinding()));
    }
    boolean returns = !"void".equals(mb.getReturnType().getName());
    List<ASTNode> pattern = new ArrayList<>();
    List<?> body = m.getBody().statements();
    if (returns) {
      if (body.size() != 1
          || !(body.get(0) instanceof ReturnStatement r)
          || r.getExpression() == null) {
        throw new Refusal(
            name
                + " returns a value from statements: fold matches a method that is one return, or"
                + " a void method's statements. Extract the expression into one first.");
      }
      pattern.add(r.getExpression());
    } else {
      if (body.isEmpty() || !Nodes.all(m.getBody(), ReturnStatement.class).isEmpty()) {
        throw new Refusal(
            name + "'s body is empty or returns early, so no run of statements is the same.");
      }
      for (Object o : body) {
        pattern.add((ASTNode) o);
      }
    }
    int[] uses = new int[params.size()];
    int[] firstEnd = new int[params.size()];
    int[] lastStart = new int[params.size()];
    List<Integer> order = new ArrayList<>();
    Set<String> bodyLocals = new HashSet<>();
    for (ASTNode p : pattern) {
      for (VariableDeclaration d : Nodes.all(p, VariableDeclaration.class)) {
        bodyLocals.add(key(d.resolveBinding()));
      }
      for (SimpleName n : Nodes.all(p, SimpleName.class)) {
        int i = params.indexOf(key(n.resolveBinding()));
        if (i >= 0) {
          if (uses[i]++ == 0) {
            firstEnd[i] = Nodes.end(n);
          }
          lastStart[i] = n.getStartPosition();
          if (!order.contains(i)) {
            order.add(i);
          }
        }
      }
    }
    for (int i = 0; i < params.size(); i++) {
      String param = ((SingleVariableDeclaration) m.parameters().get(i)).getName().getIdentifier();
      if (uses[i] == 0) {
        throw new Refusal(
            "parameter "
                + param
                + " of "
                + name
                + " is not used by its body, so no code gives it a value. Remove it first"
                + " (remove_parameter).");
      }
    }
    for (IVariableBinding w : Effects.of(m.getBody()).written()) {
      if (params.contains(key(w))) {
        throw new Refusal(
            name
                + " assigns its parameter "
                + w.getName()
                + ", which no expression can stand for.");
      }
    }
    if (returns && params.contains(key(identifier(pattern.get(0))))) {
      throw new Refusal(name + " returns its parameter, which every expression would match.");
    }

    // A parameter whose uses nothing in the body between them could change: an argument without
    // effects read there twice reads the same value, as the call's one read does.
    boolean[] stable = new boolean[params.size()];
    for (int i = 0; i < params.size(); i++) {
      stable[i] = true;
      for (ASTNode p : pattern) {
        for (Effects.Effect e : Effects.of(p).all()) {
          if (e.changes()
              && !e.deferred()
              && e.node().getStartPosition() >= firstEnd[i]
              && e.end() <= lastStart[i]) {
            stable[i] = false;
          }
        }
      }
    }
    List<Candidate> found = new ArrayList<>();
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      collect(e.getKey(), e.getValue(), m, pattern, params, bodyLocals, returns, found);
    }
    Path lineFile = in == null ? file : project.javaFile(in);
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    List<ASTNode> moved = new ArrayList<>();
    Map<Path, List<Integer>> folded = new LinkedHashMap<>();
    List<String> left = new ArrayList<>();
    List<String> reports = project.session().exceptional;
    String call = typeName + "." + methodName;
    for (Candidate c : found) {
      int at = c.unit().getLineNumber(c.start());
      if (line != null && (!c.file().equals(lineFile) || at != line)) {
        continue;
      }
      String text = holding.texts().get(c.file());
      int mark = reports.size();
      String why;
      try {
        why = leave(project, c, m, params, order, uses, stable, text, call);
      } catch (Refusal r) {
        why = r.getMessage();
      }
      if (why != null) {
        reports.subList(mark, reports.size()).clear();
        left.add(project.relative(c.file()) + " line " + at + " (" + why + ")");
        continue;
      }
      List<String> argTexts = new ArrayList<>();
      for (Expression a : c.args()) {
        argTexts.add(source(a, text));
        if (!made(a)) {
          moved.add(a);
        }
      }
      String qualifier = sameType(c.root(), mb.getDeclaringClass()) ? "" : typeName + ".";
      String replacement =
          qualifier + methodName + "(" + String.join(", ", argTexts) + ")" + (returns ? "" : ";");
      edits
          .computeIfAbsent(c.file(), k -> new ArrayList<>())
          .add(new TextEdit(c.start(), c.end(), replacement));
      folded.computeIfAbsent(c.file(), k -> new ArrayList<>()).add(at);
    }
    if (folded.isEmpty()) {
      throw new Refusal(
          "no code in the project does what "
              + name
              + " does"
              + (left.isEmpty() ? "" : " in a way a call can replace: " + String.join(", ", left))
              + ". Change the code to match the body first (extract_variable names what differs),"
              + " or the body to match the code.");
    }
    NameCheck.require(
        project,
        holding.texts(),
        edits,
        NameCheck.meanings(moved),
        "folding into calls of " + call);
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            holding.texts(),
            edits,
            "folding into calls of " + call,
            "Fold one match at a time (line), to see which one breaks.");
    int count = folded.values().stream().mapToInt(List::size).sum();
    List<String> where = new ArrayList<>();
    for (Map.Entry<Path, List<Integer>> e : folded.entrySet()) {
      where.add(
          project.relative(e.getKey())
              + (e.getValue().size() == 1 ? " line " : " lines ")
              + String.join(", ", e.getValue().stream().map(String::valueOf).toList()));
    }
    return new Outcome(
        "folded "
            + count
            + (count == 1 ? " match of " : " matches of ")
            + name
            + " into calls: "
            + String.join("; ", where)
            + (left.isEmpty() ? "" : "; left " + left.size() + ": " + String.join(", ", left)),
        declared);
  }

  /** The static method {@code type.method} of {@code unit}, or a refusal. */
  private static MethodDeclaration method(
      CompilationUnit unit, String type, String method, String name) {
    for (MethodDeclaration d : Nodes.all(unit, MethodDeclaration.class)) {
      if (d.getName().getIdentifier().equals(method)
          && d.getParent() instanceof AbstractTypeDeclaration t
          && t.getName().getIdentifier().equals(type)) {
        return d;
      }
    }
    throw new Refusal("no method " + name + " in that file. Name it as Type.method.");
  }

  /**
   * A name the body uses that is not a parameter's: a file without it cannot match. Empty, so every
   * file is read, when the body names only its parameters.
   */
  private static String anchor(MethodDeclaration m) {
    Set<String> params = new HashSet<>();
    for (Object o : m.parameters()) {
      params.add(((SingleVariableDeclaration) o).getName().getIdentifier());
    }
    for (SimpleName n : Nodes.all(m.getBody(), SimpleName.class)) {
      if (!params.contains(n.getIdentifier())
          && !(n.resolveBinding() instanceof IVariableBinding v && !v.isField())) {
        return n.getIdentifier();
      }
    }
    return "";
  }

  /** Every match of {@code pattern} in {@code unit}, outside the method itself, in source order. */
  private static void collect(
      Path file,
      CompilationUnit unit,
      MethodDeclaration m,
      List<ASTNode> pattern,
      List<String> params,
      Set<String> bodyLocals,
      boolean returns,
      List<Candidate> found) {
    String mKey = key(m.resolveBinding());
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodDeclaration d) {
            return !mKey.equals(key(d.resolveBinding()));
          }

          @Override
          public void preVisit(ASTNode n) {
            // the matching is in preVisit2
          }

          @Override
          public boolean preVisit2(ASTNode n) {
            if (returns && n instanceof Expression x && n.getClass() == pattern.get(0).getClass()) {
              Holes h = new Holes(params, bodyLocals);
              if (pattern.get(0).subtreeMatch(h, x) && !written(x)) {
                found.add(
                    new Candidate(
                        file,
                        unit,
                        x,
                        x,
                        x.getStartPosition(),
                        Nodes.end(x),
                        h.args,
                        Set.of(),
                        null,
                        0));
                return false;
              }
            }
            if (!returns && n instanceof Block b) {
              matchRuns(file, unit, b, pattern, params, bodyLocals, found);
            }
            return true;
          }
        });
  }

  /** The runs of {@code b}'s statements that match the pattern's statements. */
  private static void matchRuns(
      Path file,
      CompilationUnit unit,
      Block b,
      List<ASTNode> pattern,
      List<String> params,
      Set<String> bodyLocals,
      List<Candidate> found) {
    List<?> s = b.statements();
    int k = pattern.size();
    for (int i = 0; i + k <= s.size(); i++) {
      Holes h = new Holes(params, bodyLocals);
      boolean all = true;
      for (int j = 0; j < k && all; j++) {
        all = pattern.get(j).subtreeMatch(h, s.get(i + j));
      }
      if (all) {
        Statement firstStmt = (Statement) s.get(i);
        Statement last = (Statement) s.get(i + k - 1);
        found.add(
            new Candidate(
                file,
                unit,
                b,
                firstStmt,
                firstStmt.getStartPosition(),
                Nodes.end(last),
                h.args,
                Set.copyOf(h.mapped.values()),
                b,
                i + k));
        i += k - 1;
      }
    }
  }

  /** Why a call cannot replace {@code c}, or null when it can. */
  private static String leave(
      SourceProject project,
      Candidate c,
      MethodDeclaration m,
      List<String> params,
      List<Integer> order,
      int[] uses,
      boolean[] stable,
      String text,
      String call) {
    IMethodBinding mb = m.resolveBinding();
    if (Modifier.isPrivate(m.getModifiers()) && !sameTopLevel(c.root(), mb.getDeclaringClass())) {
      return call + " is private, and this code is in another class";
    }
    for (int i = 0; i < params.size(); i++) {
      Expression a = c.args()[i];
      ITypeBinding p = mb.getParameterTypes()[i];
      if (!fits(a, p)) {
        return "the argument " + source(a, text) + " is not a " + p.getName();
      }
      if (uses[i] > 1 && !Effects.of(a).fixed() && (Effects.of(a).changes() || !stable[i])) {
        return "the code evaluates "
            + source(a, text)
            + " "
            + uses[i]
            + " times, and a call would evaluate it once";
      }
    }
    if (overloaded(mb)) {
      // Another method of the name and arity may be more specific for an argument of a subtype
      // (or null); for arguments of the parameters' own types, this method is the one chosen.
      for (int i = 0; i < params.size(); i++) {
        Expression a = c.args()[i];
        ITypeBinding p = mb.getParameterTypes()[i];
        String type =
            made(a)
                ? "java.lang.String"
                : a.resolveTypeBinding() == null || a.resolveTypeBinding().isNullType()
                    ? null
                    : a.resolveTypeBinding().getErasure().getQualifiedName();
        if (!p.getErasure().getQualifiedName().equals(type)) {
          return "the argument "
              + source(a, text)
              + " is a "
              + (made(a) ? "String" : a.resolveTypeBinding().getName())
              + ", not the parameter's "
              + p.getName()
              + ", and "
              + mb.getDeclaringClass().getName()
              + " has another "
              + mb.getName()
              + " of "
              + params.size()
              + (params.size() == 1 ? " parameter" : " parameters")
              + " that the call could resolve to";
        }
      }
    }
    for (int x = 0; x < order.size(); x++) {
      for (int y = x + 1; y < order.size(); y++) {
        int i = order.get(x);
        int j = order.get(y);
        if (j > i) {
          continue;
        }
        Expression early = c.args()[i];
        Expression late = c.args()[j];
        if (!Effects.of(early).fixed()
            && !Effects.of(late).fixed()
            && (Effects.of(early).changes() || Effects.of(late).changes())) {
          return "the arguments "
              + source(early, text)
              + " and "
              + source(late, text)
              + " would run in another order";
        }
      }
    }
    if (c.block() != null) {
      List<?> s = c.block().statements();
      for (int i = c.after(); i < s.size(); i++) {
        for (SimpleName n : Nodes.all((ASTNode) s.get(i), SimpleName.class)) {
          if (c.locals().contains(key(n.resolveBinding()))) {
            return n.getIdentifier() + " is declared by the code and used after it";
          }
        }
      }
    }
    List<Expression> before = new ArrayList<>();
    for (int i = 0; i < params.size(); i++) {
      Expression a = c.args()[i];
      if (made(a)) {
        continue;
      }
      String param = ((SingleVariableDeclaration) m.parameters().get(i)).getName().getIdentifier();
      Behavior.flow(
          project,
          new Behavior.Move(
              a,
              c.root(),
              List.of(c.landing()),
              "the argument " + source(a, text) + " for " + param,
              "folding it into a call of " + call,
              false,
              List.copyOf(before)));
      before.add(a);
    }
    return null;
  }

  /**
   * Whether a method other than {@code m}, of its name and number of parameters, is in its class or
   * a supertype: a call could resolve to it.
   */
  private static boolean overloaded(IMethodBinding m) {
    String key = m.getMethodDeclaration().getKey();
    List<ITypeBinding> todo = new ArrayList<>(List.of(m.getDeclaringClass()));
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding t = todo.get(i);
      for (IMethodBinding other : t.getDeclaredMethods()) {
        if (other.getName().equals(m.getName())
            && other.getParameterTypes().length == m.getParameterTypes().length
            && !other.getMethodDeclaration().getKey().equals(key)) {
          return true;
        }
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return false;
  }

  /**
   * The matcher: a parameter of the body matches any expression (the same one each time), a local
   * the body declares matches one local of the code, and any other name matches the same name with
   * the same meaning, by binding key (the two may come from different parses).
   */
  private static final class Holes extends ASTMatcher {
    final List<String> params;
    final Set<String> bodyLocals;
    final Expression[] args;
    final Map<String, String> mapped = new HashMap<>();

    Holes(List<String> params, Set<String> bodyLocals) {
      super(false);
      this.params = params;
      this.bodyLocals = bodyLocals;
      this.args = new Expression[params.size()];
    }

    @Override
    public boolean match(SimpleName p, Object other) {
      String key = key(p.resolveBinding());
      int i = key == null ? -1 : params.indexOf(key);
      if (i >= 0) {
        if (!(other instanceof Expression x)) {
          return false;
        }
        if (args[i] == null) {
          args[i] = x;
          return true;
        }
        return args[i].subtreeMatch(new ASTMatcher(), x);
      }
      if (!(other instanceof SimpleName o)) {
        return false;
      }
      String theirs = key(o.resolveBinding());
      if (key != null && bodyLocals.contains(key)) {
        if (theirs == null) {
          return false;
        }
        String was = mapped.get(key);
        if (was == null) {
          if (mapped.containsValue(theirs)) {
            return false;
          }
          mapped.put(key, theirs);
          return true;
        }
        return was.equals(theirs);
      }
      return p.getIdentifier().equals(o.getIdentifier()) && key != null && key.equals(theirs);
    }

    /**
     * A call matches as usual, or, for String.format on both sides, with each plain %s of the
     * body's format whose argument is a parameter matching constant text in the code's format, the
     * parameter then standing for that text: "%s: %s", room, what matches "%s: temp", room.
     */
    @Override
    public boolean match(MethodInvocation p, Object other) {
      Expression[] savedArgs = args.clone();
      Map<String, String> savedMapped = new HashMap<>(mapped);
      if (super.match(p, other)) {
        return true;
      }
      restore(savedArgs, savedMapped);
      if (other instanceof MethodInvocation o && isFormat(p) && isFormat(o) && format(p, o)) {
        return true;
      }
      restore(savedArgs, savedMapped);
      return false;
    }

    private void restore(Expression[] savedArgs, Map<String, String> savedMapped) {
      System.arraycopy(savedArgs, 0, args, 0, args.length);
      mapped.clear();
      mapped.putAll(savedMapped);
    }

    private boolean format(MethodInvocation p, MethodInvocation o) {
      if (!safeSubtreeMatch(p.getExpression(), o.getExpression())
          || !safeSubtreeMatch(p.getName(), o.getName())) {
        return false;
      }
      List<?> pa = p.arguments();
      List<?> oa = o.arguments();
      int f = 0;
      while (f < pa.size() && !(pa.get(f) instanceof StringLiteral)) {
        f++;
      }
      if (f == pa.size() || f >= oa.size() || !(oa.get(f) instanceof StringLiteral theirs)) {
        return false;
      }
      for (int i = 0; i < f; i++) {
        if (!safeSubtreeMatch(pa.get(i), oa.get(i))) {
          return false;
        }
      }
      String pf = ((StringLiteral) pa.get(f)).getLiteralValue();
      StringBuilder regex = new StringBuilder();
      List<Integer> relaxed = new ArrayList<>();
      Matcher spec = SPEC.matcher(pf);
      int last = 0;
      int arg = 0;
      while (spec.find()) {
        regex.append(Pattern.quote(pf.substring(last, spec.start())));
        String s = spec.group();
        if (s.equals("%%") || s.equals("%n")) {
          regex.append(Pattern.quote(s));
        } else if (s.contains("$")) {
          return false;
        } else {
          int at = f + 1 + arg;
          boolean hole =
              s.equals("%s")
                  && at < pa.size()
                  && pa.get(at) instanceof SimpleName n
                  && params.contains(key(n.resolveBinding()));
          // A hole's %s stays a %s with its argument, or is constant text in its place.
          regex.append(hole ? "(%s|[^%]*)" : Pattern.quote(s));
          if (hole) {
            relaxed.add(arg);
          }
          arg++;
        }
        last = spec.end();
      }
      regex.append(Pattern.quote(pf.substring(last)));
      Matcher text =
          Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(theirs.getLiteralValue());
      if (relaxed.isEmpty() || !text.matches()) {
        return false;
      }
      int group = 0;
      int next = f + 1;
      for (int k = 0; k < pa.size() - f - 1; k++) {
        Object pk = pa.get(f + 1 + k);
        String captured = relaxed.contains(k) ? text.group(++group) : null;
        if (captured != null && !captured.equals("%s")) {
          int i = params.indexOf(key(((SimpleName) pk).resolveBinding()));
          StringLiteral made = o.getAST().newStringLiteral();
          made.setLiteralValue(captured);
          if (args[i] == null) {
            args[i] = made;
          } else if (!(args[i] instanceof StringLiteral s
              && s.getLiteralValue().equals(made.getLiteralValue()))) {
            return false;
          }
        } else {
          if (next >= oa.size() || !safeSubtreeMatch(pk, oa.get(next))) {
            return false;
          }
          next++;
        }
      }
      return next == oa.size();
    }

    private static final Pattern SPEC =
        Pattern.compile("%(\\d+\\$)?[-#+ 0,(<]*\\d*(\\.\\d+)?[a-zA-Z%]");

    private static boolean isFormat(MethodInvocation m) {
      IMethodBinding b = m.resolveMethodBinding();
      return b != null
          && b.getName().equals("format")
          && Modifier.isStatic(b.getModifiers())
          && b.getDeclaringClass().getQualifiedName().equals("java.lang.String");
    }
  }

  /** Whether {@code x} is written: an assignment's target, or what ++ or -- changes. */
  private static boolean written(Expression x) {
    ASTNode p = x.getParent();
    return (p instanceof Assignment a && a.getLeftHandSide() == x)
        || (p instanceof PostfixExpression)
        || (p instanceof PrefixExpression pre
            && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                || pre.getOperator() == PrefixExpression.Operator.DECREMENT));
  }

  /** Whether {@code x}'s type is {@code param}, or for a reference, a subtype of it, by name. */
  private static boolean fits(Expression x, ITypeBinding param) {
    if (made(x)) {
      return param.getQualifiedName().equals("java.lang.String");
    }
    ITypeBinding t = x.resolveTypeBinding();
    if (t == null) {
      return false;
    }
    if (t.isNullType()) {
      return !param.isPrimitive();
    }
    if (t.isPrimitive() || param.isPrimitive()) {
      return t.getKey().equals(param.getKey());
    }
    String target = param.getErasure().getQualifiedName();
    List<ITypeBinding> todo = new ArrayList<>(List.of(t));
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding s = todo.get(i);
      if (s.getErasure().getQualifiedName().equals(target) || target.equals("java.lang.Object")) {
        return true;
      }
      if (s.getSuperclass() != null) {
        todo.add(s.getSuperclass());
      }
      todo.addAll(List.of(s.getInterfaces()));
    }
    return false;
  }

  /** Whether {@code n} is directly in {@code type}, so the call needs no qualifier. */
  private static boolean sameType(ASTNode n, ITypeBinding type) {
    for (ASTNode p = n; p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t) {
        ITypeBinding b = t.resolveBinding();
        return b != null && b.getErasure().getKey().equals(type.getErasure().getKey());
      }
    }
    return false;
  }

  /** Whether {@code n} is in the top-level type that declares {@code type}, or in it. */
  private static boolean sameTopLevel(ASTNode n, ITypeBinding type) {
    ITypeBinding top = type;
    while (top.getDeclaringClass() != null) {
      top = top.getDeclaringClass();
    }
    for (ASTNode p = n; p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t && t.getParent() instanceof CompilationUnit) {
        ITypeBinding b = t.resolveBinding();
        return b != null && b.getErasure().getKey().equals(top.getErasure().getKey());
      }
    }
    return false;
  }

  /** The simple name {@code n} is, or null. */
  private static IBinding identifier(ASTNode n) {
    return n instanceof SimpleName s ? s.resolveBinding() : null;
  }

  /** The text of {@code x}: its source, or for a literal fold made from a format, its literal. */
  private static String source(Expression x, String text) {
    return made(x)
        ? ((StringLiteral) x).getEscapedValue()
        : text.substring(x.getStartPosition(), Nodes.end(x));
  }

  /** Whether {@code x} is a literal fold made from a format's text, which is not in the source. */
  private static boolean made(Expression x) {
    return x.getStartPosition() < 0;
  }

  private static String key(IBinding b) {
    return switch (b) {
      case null -> null;
      case ITypeBinding t -> t.getTypeDeclaration().getKey();
      case IMethodBinding mb -> mb.getMethodDeclaration().getKey();
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      default -> b.getKey();
    };
  }
}
