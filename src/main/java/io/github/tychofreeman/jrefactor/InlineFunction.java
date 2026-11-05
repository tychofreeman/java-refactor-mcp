package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
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
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * inline_function: a static method is replaced at each of its calls in the project by its body,
 * with the arguments in place of the parameters, and the method is deleted. The first version takes
 * a body of one {@code return} or one statement, and refuses what it does not rewrite.
 */
final class InlineFunction {

  private InlineFunction() {}

  /** One use of a parameter in the body: the range of its name and which parameter it is. */
  private record ParamUse(int start, int end, int index) {}

  static Outcome run(SourceProject project, String rel, String name) throws Exception {
    Qualified q = Qualified.of(name, "Type.method, the class and the static method to inline");
    String typeName = q.type();
    String member = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    TypeDeclaration type = opened.topLevel(typeName);
    BodyDeclaration declared = MemberMove.member(type, typeName, member, rel, "inline_function");
    Precondition.require(
        Precondition.that(
            () -> declared instanceof MethodDeclaration,
            () -> name + " is a field. inline_function inlines a method."));
    MethodDeclaration m = (MethodDeclaration) declared;
    IMethodBinding method = m.resolveBinding();
    Precondition.require(
        Precondition.that(() -> method != null, () -> name + " does not resolve."),
        Precondition.that(
            () -> Modifier.isStatic(m.getModifiers()),
            () ->
                name
                    + " is not static. inline_function inlines static methods in this first"
                    + " version; an instance method needs its receiver."),
        Precondition.that(
            () -> !m.isVarargs() && m.typeParameters().isEmpty(),
            () -> name + " is varargs or generic, which inline_function does not inline yet."),
        Precondition.that(
            () -> m.getBody() != null,
            () -> name + " has no body (abstract or native), so there is nothing to inline."));
    List<Statement> statements = typed(m.getBody().statements(), Statement.class);
    Expression value = null;
    Statement body;
    if (statements.size() == 1
        && statements.get(0) instanceof ReturnStatement r
        && r.getExpression() != null) {
      value = r.getExpression();
      body = r;
    } else if (statements.size() == 1 && statements.get(0) instanceof ExpressionStatement s) {
      body = s;
    } else {
      throw new Refusal(
          name
              + " has a body that is not one return or one statement. Inline its parts by hand, or"
              + " shorten the method first.");
    }
    if (callsItself(m, method)) {
      throw new Refusal(name + " calls itself, so inlining it would not end. Keep it.");
    }

    Path declaringFile = file;
    String declaringText = text;
    List<Path> candidates =
        new ArrayList<>(
            new LinkedHashSet<>(
                project.javaFiles().stream().map(p -> p.toAbsolutePath().normalize()).toList()));
    Map<Path, String> texts = new LinkedHashMap<>();
    List<Path> toParse = new ArrayList<>();
    for (Path p : candidates) {
      String content = p.equals(file) ? text : Files.readString(p);
      if (p.equals(file) || content.contains(member)) {
        texts.put(p, content);
        toParse.add(p);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, toParse);

    List<SingleVariableDeclaration> params = typed(m.parameters(), SingleVariableDeclaration.class);
    List<IVariableBinding> paramBindings = new ArrayList<>();
    for (SingleVariableDeclaration p : params) {
      paramBindings.add(p.resolveBinding());
    }
    ASTNode bodyNode = value != null ? value : body;
    int bodyStart = bodyNode.getStartPosition();
    int bodyEnd = bodyStart + bodyNode.getLength();
    List<ParamUse> uses = paramUsesIn(value != null ? value : body, paramBindings);
    int[] useCount = new int[params.size()];
    for (ParamUse u : uses) {
      useCount[u.index()]++;
    }

    boolean usesOwnMembers = usesMembersOf(bodyNode, type.resolveBinding());

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    int calls = 0;
    for (Path p : parsed.keySet()) {
      CompilationUnit cu = parsed.get(p);
      String fileText = texts.get(p);
      List<MethodInvocation> sites = new ArrayList<>();
      List<ASTNode> refs = new ArrayList<>();
      cu.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
              IMethodBinding b = node.resolveMethodBinding();
              if (b != null && b.getKey().equals(method.getKey())) {
                sites.add(node);
              }
              return true;
            }

            @Override
            public boolean preVisit2(ASTNode node) {
              if (node instanceof MethodReference r) {
                IMethodBinding b = r.resolveMethodBinding();
                if (b != null && b.getKey().equals(method.getKey())) {
                  refs.add(r);
                }
              }
              return true;
            }
          });
      if (!refs.isEmpty()) {
        throw new Refusal(
            name
                + " is used as a method reference (Type::name) in "
                + project.relative(p)
                + ", which inline_function cannot rewrite. Change it to a call first.");
      }
      if (sites.isEmpty()) {
        continue;
      }
      List<TextEdit> list = new ArrayList<>();
      for (MethodInvocation site : sites) {
        if (usesOwnMembers && !(p.equals(file) && within(site, type))) {
          throw new Refusal(
              "the body of "
                  + name
                  + " uses a member of "
                  + typeName
                  + ", and the call in "
                  + project.relative(p)
                  + " is in another class, where that name would mean something else. Keep the"
                  + " method, or qualify its uses in the body first.");
        }
        checkNesting(site, sites, name);
        list.add(
            replacementFor(
                site,
                value,
                body,
                bodyStart,
                bodyEnd,
                uses,
                useCount,
                params,
                fileText,
                name,
                declaringText));
        calls++;
      }
      edits.computeIfAbsent(p, k -> new ArrayList<>()).addAll(list);
    }
    if (calls == 0) {
      throw new Refusal(
          name + " has no calls in the project. Delete it with replace_declaration instead.");
    }
    edits.computeIfAbsent(declaringFile, k -> new ArrayList<>()).add(removal(declaringText, m));

    Stubs.Declared declaredStubs =
        MemberMove.gate(
            project,
            texts,
            edits,
            "inlining " + name,
            "Keep the method, or change its callers first.");
    return new Outcome(
        "inlined "
            + name
            + " into "
            + calls
            + (calls == 1 ? " call" : " calls")
            + " and deleted the method in "
            + rel,
        declaredStubs);
  }

  /** The edit that replaces one call with the body, its parameters given the call's arguments. */
  private static TextEdit replacementFor(
      MethodInvocation site,
      Expression value,
      Statement body,
      int bodyStart,
      int bodyEnd,
      List<ParamUse> uses,
      int[] useCount,
      List<SingleVariableDeclaration> params,
      String siteText,
      String name,
      String declaringText) {
    List<Expression> args = typed(site.arguments(), Expression.class);
    checkArguments(args, params, useCount, name);
    checkTiming(args, uses, params, value != null ? value : body, siteText, name);
    checkOrder(args, uses, params, name);
    for (int i = 0; i < args.size(); i++) {
      if (useCount[i] > 1 && ExtractVariable.hasEffect(args.get(i))) {
        throw new Refusal(
            "parameter "
                + params.get(i).getName().getIdentifier()
                + " is used "
                + useCount[i]
                + " times in the body of "
                + name
                + ", and its argument calls, assigns or changes a variable, so the call would run"
                + " more than once. Put the argument in a variable first.");
      }
    }
    String bodyText = declaringText.substring(bodyStart, bodyEnd);
    StringBuilder out = new StringBuilder(bodyText);
    List<ParamUse> sorted = new ArrayList<>(uses);
    sorted.sort((a, b) -> Integer.compare(b.start(), a.start()));
    for (ParamUse u : sorted) {
      Expression arg = args.get(u.index());
      String argText =
          siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
      if (InlineVariable.needsParentheses(arg)) {
        argText = "(" + argText + ")";
      }
      out.replace(u.start() - bodyStart, u.end() - bodyStart, argText);
    }
    String replaced = out.toString();
    if (value != null) {
      if (site.getParent() instanceof ExpressionStatement) {
        throw new Refusal(
            name
                + " returns a value, and a call of it is a statement. Use its result, or keep the"
                + " method.");
      }
      if (InlineVariable.needsParentheses(value)) {
        replaced = "(" + replaced + ")";
      }
      return new TextEdit(
          site.getStartPosition(), site.getStartPosition() + site.getLength(), replaced);
    }
    if (!(site.getParent() instanceof ExpressionStatement stmt)) {
      throw new Refusal(name + " returns nothing, so a call of it can only be a statement.");
    }
    return new TextEdit(
        stmt.getStartPosition(), stmt.getStartPosition() + stmt.getLength(), replaced);
  }

  /**
   * Refuses arguments that inlining would treat differently: one whose call the body drops, two
   * whose calls would run in another order, and one whose type the call converts.
   */
  private static void checkArguments(
      List<Expression> args, List<SingleVariableDeclaration> params, int[] useCount, String name) {
    int effects = 0;
    for (int i = 0; i < args.size(); i++) {
      Expression arg = args.get(i);
      String param = params.get(i).getName().getIdentifier();
      boolean effect = ExtractVariable.hasEffect(arg);
      if (useCount[i] == 0 && effect) {
        throw new Refusal(
            "parameter "
                + param
                + " is not used in the body of "
                + name
                + ", and its argument calls, assigns or changes a variable, so inlining would drop"
                + " that effect. Keep the call, or use the argument in the body first.");
      }
      ITypeBinding argType = arg.resolveTypeBinding();
      ITypeBinding paramType = params.get(i).resolveBinding().getType();
      if (argType != null
          && !argType.isNullType()
          && paramType != null
          && !argType.isEqualTo(paramType)) {
        throw new Refusal(
            "argument "
                + param
                + " of "
                + name
                + " has type "
                + argType.getName()
                + ", and the parameter is "
                + paramType.getName()
                + ". The call converts to "
                + paramType.getName()
                + ", and the body would not. Pass a value of the parameter's type first.");
      }
      if (effect) {
        effects++;
      }
    }
    if (effects > 1) {
      throw new Refusal(
          "two arguments of "
              + name
              + " call, assign or change a variable, and inlining would run them in another order."
              + " Put one of them in a variable first.");
    }
  }

  /**
   * Refuses an argument that is not {@link #fixed} when the inlined body would evaluate it at
   * another time than the call did: after an effect of the body, or inside a lambda or anonymous
   * class (later), or, for one with an effect, in a branch (perhaps not at all).
   */
  private static void checkTiming(
      List<Expression> args,
      List<ParamUse> uses,
      List<SingleVariableDeclaration> params,
      ASTNode bodyNode,
      String siteText,
      String name) {
    List<ASTNode> effects = effectsIn(bodyNode);
    for (ParamUse u : uses) {
      Expression arg = args.get(u.index());
      if (fixed(arg)) {
        continue;
      }
      String param = params.get(u.index()).getName().getIdentifier();
      String source =
          siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
      ASTNode use = NodeFinder.perform(bodyNode.getRoot(), u.start(), u.end() - u.start());
      String where = deferral(use, bodyNode, ExtractVariable.hasEffect(arg));
      if (where != null) {
        throw new Refusal(
            "the argument "
                + source
                + " for "
                + param
                + " calls, assigns or reads changing state, and the body of "
                + name
                + " uses "
                + param
                + " "
                + where
                + ", so it would run at another time, or not at all. Put the argument in a"
                + " variable first.");
      }
      for (ASTNode e : effects) {
        if (e.getStartPosition() + e.getLength() <= u.start()) {
          throw new Refusal(
              "the argument "
                  + source
                  + " for "
                  + param
                  + " calls, assigns or reads changing state, and "
                  + e
                  + " in the body of "
                  + name
                  + " runs before its use, so the order would change. Put the argument in a"
                  + " variable first.");
        }
      }
    }
  }

  /**
   * Refuses arguments that are not {@link #fixed}, one of them with an effect, that the body first
   * uses in the other order than the call passes them: the read would move to the other side of the
   * effect.
   */
  private static void checkOrder(
      List<Expression> args,
      List<ParamUse> uses,
      List<SingleVariableDeclaration> params,
      String name) {
    int[] first = new int[args.size()];
    Arrays.fill(first, -1);
    for (ParamUse u : uses) {
      if (first[u.index()] < 0 || u.start() < first[u.index()]) {
        first[u.index()] = u.start();
      }
    }
    for (int i = 0; i < args.size(); i++) {
      if (first[i] < 0 || fixed(args.get(i))) {
        continue;
      }
      for (int j = i + 1; j < args.size(); j++) {
        if (first[j] < 0 || fixed(args.get(j)) || first[j] > first[i]) {
          continue;
        }
        if (ExtractVariable.hasEffect(args.get(i)) || ExtractVariable.hasEffect(args.get(j))) {
          throw new Refusal(
              "the body of "
                  + name
                  + " uses "
                  + params.get(j).getName().getIdentifier()
                  + " before "
                  + params.get(i).getName().getIdentifier()
                  + ", and one of their arguments calls, assigns or changes a variable that the"
                  + " other may read, so inlining would evaluate them in the other order. Put the"
                  + " argument with the effect in a variable first.");
        }
      }
    }
  }

  /**
   * Where {@code use} runs later or only sometimes, as words ("inside a lambda"), or null when it
   * runs once, in order. Branches count only for an argument with an effect.
   */
  private static String deferral(ASTNode use, ASTNode bodyNode, boolean effect) {
    for (ASTNode n = use, p = use.getParent();
        n != bodyNode && p != null;
        n = p, p = p.getParent()) {
      if (p instanceof LambdaExpression) {
        return "inside a lambda";
      }
      if (p instanceof AnonymousClassDeclaration) {
        return "inside an anonymous class";
      }
      if (effect && p instanceof ConditionalExpression c && n != c.getExpression()) {
        return "in a branch of ?:";
      }
      if (effect
          && p instanceof InfixExpression in
          && n != in.getLeftOperand()
          && (in.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
              || in.getOperator() == InfixExpression.Operator.CONDITIONAL_OR)) {
        return "to the right of && or ||";
      }
    }
    return null;
  }

  /**
   * Whether {@code e} is literals, locals, parameters, constants and operators on them: nothing the
   * body could change or run twice. A lambda counts as fixed, since making one runs nothing.
   */
  private static boolean fixed(Expression e) {
    boolean[] fixed = {true};
    e.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(LambdaExpression n) {
            return false;
          }

          @Override
          public void preVisit(ASTNode n) {
            if (n instanceof MethodInvocation
                || n instanceof SuperMethodInvocation
                || n instanceof ClassInstanceCreation
                || n instanceof ArrayCreation
                || n instanceof Assignment
                || n instanceof PostfixExpression
                || n instanceof ArrayAccess
                || n instanceof FieldAccess
                || n instanceof SuperFieldAccess
                || (n instanceof PrefixExpression p && changes(p))
                || (n instanceof SimpleName s
                    && s.resolveBinding() instanceof IVariableBinding v
                    && v.isField()
                    && v.getConstantValue() == null)) {
              fixed[0] = false;
            }
          }
        });
    return fixed[0];
  }

  private static boolean changes(PrefixExpression p) {
    return p.getOperator() == PrefixExpression.Operator.INCREMENT
        || p.getOperator() == PrefixExpression.Operator.DECREMENT;
  }

  /**
   * The calls, creations, assignments and increments in {@code node}, outside lambdas and anonymous
   * classes (which run later, if at all).
   */
  private static List<ASTNode> effectsIn(ASTNode node) {
    List<ASTNode> out = new ArrayList<>();
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(LambdaExpression n) {
            return false;
          }

          @Override
          public boolean visit(AnonymousClassDeclaration n) {
            return false;
          }

          @Override
          public void preVisit(ASTNode n) {
            if (n instanceof MethodInvocation
                || n instanceof SuperMethodInvocation
                || n instanceof ClassInstanceCreation
                || n instanceof Assignment
                || n instanceof PostfixExpression
                || (n instanceof PrefixExpression p && changes(p))) {
              out.add(n);
            }
          }
        });
    return out;
  }

  /** Whether {@code node} names a field or method that {@code owner} declares. */
  private static boolean usesMembersOf(ASTNode node, ITypeBinding owner) {
    boolean[] found = {false};
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            IBinding b = n.resolveBinding();
            if (b instanceof IVariableBinding v
                && v.isField()
                && owner.isEqualTo(v.getDeclaringClass())) {
              found[0] = true;
            }
            if (b instanceof IMethodBinding m && owner.isEqualTo(m.getDeclaringClass())) {
              found[0] = true;
            }
            return true;
          }
        });
    return found[0];
  }

  /** Refuses a call inside another call's arguments, whose edits would overlap. */
  private static void checkNesting(
      MethodInvocation site, List<MethodInvocation> sites, String name) {
    for (MethodInvocation other : sites) {
      if (other != site && within(other, site)) {
        throw new Refusal(
            "a call of "
                + name
                + " is inside another call of it. Inline the inner call first, in a batch, or name"
                + " the inner result in a variable.");
      }
    }
  }

  private static boolean within(ASTNode inner, ASTNode outer) {
    int start = outer.getStartPosition();
    return inner.getStartPosition() >= start
        && inner.getStartPosition() + inner.getLength() <= start + outer.getLength();
  }

  /** The parameter uses in {@code node}: each name that is one of the parameters. */
  private static List<ParamUse> paramUsesIn(ASTNode node, List<IVariableBinding> params) {
    List<ParamUse> uses = new ArrayList<>();
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (n.resolveBinding() instanceof IVariableBinding v) {
              for (int i = 0; i < params.size(); i++) {
                if (params.get(i) != null && params.get(i).isEqualTo(v)) {
                  uses.add(
                      new ParamUse(n.getStartPosition(), n.getStartPosition() + n.getLength(), i));
                }
              }
            }
            return true;
          }
        });
    return uses;
  }

  /** The elements of a raw JDT list, each checked to be a {@code type}. */
  private static <T> List<T> typed(List<?> list, Class<T> type) {
    List<T> out = new ArrayList<>();
    for (Object o : list) {
      out.add(type.cast(o));
    }
    return out;
  }

  /** Whether the body calls the method itself. */
  private static boolean callsItself(MethodDeclaration m, IMethodBinding method) {
    boolean[] found = {false};
    m.getBody()
        .accept(
            new ASTVisitor() {
              @Override
              public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                if (b != null && b.getKey().equals(method.getKey())) {
                  found[0] = true;
                }
                return true;
              }
            });
    return found[0];
  }

  /**
   * The edit that deletes {@code m} with its lines, and the blank line after it when there is one.
   */
  private static TextEdit removal(String text, MethodDeclaration m) {
    int start = text.lastIndexOf('\n', m.getStartPosition() - 1) + 1;
    int last = m.getStartPosition() + m.getLength() - 1;
    int end = text.indexOf('\n', last);
    end = end < 0 ? text.length() : end + 1;
    int nl = text.indexOf('\n', end);
    if (nl >= 0 && text.substring(end, nl).isBlank()) {
      end = nl + 1;
    }
    return new TextEdit(start, end, "");
  }
}
