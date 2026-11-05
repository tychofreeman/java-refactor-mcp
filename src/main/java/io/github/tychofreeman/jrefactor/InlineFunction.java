package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

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
            () -> !m.isVarargs() && m.typeParameters().isEmpty(),
            () -> name + " is varargs or generic, which inline_function does not inline yet."),
        Precondition.that(
            () -> m.getBody() != null,
            () -> name + " has no body (abstract or native), so there is nothing to inline."));
    List<Statement> statements = typed(m.getBody().statements(), Statement.class);
    Statement last = statements.isEmpty() ? null : statements.get(statements.size() - 1);
    int returns = returnsIn(m.getBody());
    // The statements before the last: a call of several statements puts them before its own.
    List<Statement> prefix =
        statements.isEmpty() ? List.of() : statements.subList(0, statements.size() - 1);
    Expression value = null;
    Statement body;
    if (last instanceof ReturnStatement r && r.getExpression() != null && returns == 1) {
      value = r.getExpression();
      body = r;
    } else if (last != null && returns == 0) {
      body = last;
    } else {
      throw new Refusal(
          name
              + " has a body that is empty or returns before its last statement. Inline its parts"
              + " by hand, or end it in its one return first.");
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
    int bodyStart = value != null ? value.getStartPosition() : statements.get(0).getStartPosition();
    int bodyEnd = bodyNode.getStartPosition() + bodyNode.getLength();
    List<ParamUse> uses = paramUsesIn(m.getBody(), paramBindings);
    checkNotAssigned(uses, unit, params, name);
    // A parameter used only as a call's receiver dispatches on the argument's own type, so a
    // subtype argument means the same there.
    boolean[] receiverOnly = new boolean[params.size()];
    for (int i = 0; i < params.size(); i++) {
      receiverOnly[i] = useCount(uses, i) > 0;
    }
    for (ParamUse u : uses) {
      ASTNode n = NodeFinder.perform(unit, u.start(), u.end() - u.start());
      if (!(n.getParent() instanceof MethodInvocation mi && mi.getExpression() == n)) {
        receiverOnly[u.index()] = false;
      }
    }
    Set<String> bodyLocals = declaredIn(m.getBody());
    for (SingleVariableDeclaration p : params) {
      bodyLocals.remove(p.getName().getIdentifier());
    }
    int[] useCount = new int[params.size()];
    for (ParamUse u : uses) {
      useCount[u.index()]++;
    }

    boolean usesOwnMembers = usesMembersOf(m.getBody(), type.resolveBinding());
    boolean instance = !Modifier.isStatic(m.getModifiers());
    List<ASTNode> thisUses = new ArrayList<>();
    List<SimpleName> implicit = new ArrayList<>();
    if (instance) {
      receiverUses(m.getBody(), type.resolveBinding(), name, thisUses, implicit);
      refuseOverridden(parsed, file, method, name);
    }
    List<String> nullable = new ArrayList<>();
    // A parameter the body uses never, or only in a branch or later: its argument may not run.
    boolean[] sometimes = new boolean[params.size()];
    for (int i = 0; i < params.size(); i++) {
      sometimes[i] = true;
    }
    for (ParamUse u : uses) {
      ASTNode n = NodeFinder.perform(unit, u.start(), u.end() - u.start());
      if (deferral(n, m.getBody(), true) == null) {
        sometimes[u.index()] = false;
      }
    }
    List<String> throwsSometimes = new ArrayList<>();
    // What the moved text means where it is written: the body, and each call's arguments.
    List<ASTNode> fragments = new ArrayList<>(List.of(m.getBody()));

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
        for (int i = 0; i < site.arguments().size() && i < sometimes.length; i++) {
          Expression a = (Expression) site.arguments().get(i);
          if (sometimes[i] && !Effects.of(a).mayThrow().isEmpty()) {
            throwsSometimes.add(a + " in " + project.relative(p));
          }
        }
        for (Object a : site.arguments()) {
          fragments.add((ASTNode) a);
        }
        if (site.getExpression() != null) {
          fragments.add(site.getExpression());
        }
        Expression receiver = site.getExpression();
        if (instance
            && receiver != null
            && !(receiver instanceof ThisExpression)
            && !(receiver instanceof ClassInstanceCreation)) {
          nullable.add(receiver + " in " + project.relative(p));
        }
        list.addAll(
            replacementFor(
                site,
                value,
                m.getBody(),
                prefix,
                bodyLocals,
                instance ? thisUses : List.of(),
                instance ? implicit : List.of(),
                bodyStart,
                bodyEnd,
                uses,
                useCount,
                params,
                fileText,
                name,
                declaringText,
                receiverOnly));
        calls++;
      }
      edits.computeIfAbsent(p, k -> new ArrayList<>()).addAll(list);
    }
    if (calls == 0) {
      throw new Refusal(
          name + " has no calls in the project. Delete it with replace_declaration instead.");
    }
    if (!throwsSometimes.isEmpty()) {
      Exceptional.change(
          project,
          "an argument of "
              + name
              + " that may throw is not used by the inlined body, or only in a branch, so it"
              + " would throw there only sometimes, or never: "
              + String.join(", ", throwsSometimes),
          "keep the method");
    }
    if (!nullable.isEmpty()) {
      Exceptional.change(
          project,
          "a null receiver of "
              + name
              + " now fails where the inlined body uses it (or not at all), not at the call: "
              + String.join(", ", nullable),
          "keep the method");
    }
    edits.computeIfAbsent(declaringFile, k -> new ArrayList<>()).add(removal(declaringText, m));

    NameCheck.require(project, texts, edits, NameCheck.meanings(fragments), "inlining " + name);
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

  /**
   * The edits that replace one call with the body, its parameters given the call's arguments: the
   * call itself, and for a body of several statements, those before the last put before the call's
   * statement.
   */
  private static List<TextEdit> replacementFor(
      MethodInvocation site,
      Expression value,
      Block whole,
      List<Statement> prefix,
      Set<String> bodyLocals,
      List<ASTNode> thisUses,
      List<SimpleName> implicit,
      int bodyStart,
      int bodyEnd,
      List<ParamUse> uses,
      int[] useCount,
      List<SingleVariableDeclaration> params,
      String siteText,
      String name,
      String declaringText,
      boolean[] receiverOnly) {
    List<Expression> args = typed(site.arguments(), Expression.class);
    checkArguments(args, params, useCount, name, receiverOnly);
    checkTiming(args, uses, params, whole, siteText, name);
    checkOrder(args, uses, params, name);
    for (int i = 0; i < args.size(); i++) {
      if (useCount[i] > 1 && Effects.of(args.get(i)).changes()) {
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
    checkLocals(site, bodyLocals, name);
    List<TextEdit> receiver =
        new ArrayList<>(receiverEdits(site, thisUses, implicit, siteText, name));
    receiver.addAll(shadowEdits(site, whole, receiver, name.substring(0, name.indexOf('.'))));
    if (value != null) {
      if (site.getParent() instanceof ExpressionStatement) {
        throw new Refusal(
            name
                + " returns a value, and a call of it is a statement. Use its result, or keep the"
                + " method.");
      }
      String replaced =
          substituted(declaringText, bodyStart, bodyEnd, uses, args, siteText, receiver);
      // A call that is all of an initializer, a return or an assignment's value needs none.
      boolean entire =
          site.getParent() instanceof VariableDeclarationFragment
              || site.getParent() instanceof ReturnStatement
              || (site.getParent() instanceof Assignment a && a.getRightHandSide() == site);
      if (!entire && InlineVariable.needsParentheses(value)) {
        replaced = "(" + replaced + ")";
      }
      TextEdit call =
          new TextEdit(
              site.getStartPosition(), site.getStartPosition() + site.getLength(), replaced);
      if (prefix.isEmpty()) {
        return List.of(call);
      }
      Statement statement = wholeStatement(site, name);
      int from = prefix.get(0).getStartPosition();
      Statement end = prefix.get(prefix.size() - 1);
      String before =
          reindent(
              substituted(
                  declaringText,
                  from,
                  end.getStartPosition() + end.getLength(),
                  uses,
                  args,
                  siteText,
                  receiver),
              indentOf(declaringText, from),
              indentOf(siteText, statement.getStartPosition()));
      int at = statement.getStartPosition();
      return List.of(new TextEdit(at, at, before + "\n" + indentOf(siteText, at)), call);
    }
    if (!(site.getParent() instanceof ExpressionStatement stmt)) {
      throw new Refusal(name + " returns nothing, so a call of it can only be a statement.");
    }
    String replaced =
        reindent(
            substituted(declaringText, bodyStart, bodyEnd, uses, args, siteText, receiver),
            indentOf(declaringText, bodyStart),
            indentOf(siteText, stmt.getStartPosition()));
    return List.of(
        new TextEdit(
            stmt.getStartPosition(), stmt.getStartPosition() + stmt.getLength(), replaced));
  }

  /**
   * The text of {@code start}-{@code end} of the declaring file, each parameter use given its
   * argument, and the receiver's edits ({@code receiver}, at positions of the declaring file) made.
   */
  private static String substituted(
      String declaringText,
      int start,
      int end,
      List<ParamUse> uses,
      List<Expression> args,
      String siteText,
      List<TextEdit> receiver) {
    List<TextEdit> all = new ArrayList<>(receiver);
    for (ParamUse u : uses) {
      Expression arg = args.get(u.index());
      String argText =
          siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
      if (InlineVariable.needsParentheses(arg)) {
        argText = "(" + argText + ")";
      }
      all.add(new TextEdit(u.start(), u.end(), argText));
    }
    all.sort((a, b) -> Integer.compare(b.start(), a.start()));
    StringBuilder out = new StringBuilder(declaringText.substring(start, end));
    for (TextEdit e : all) {
      if (e.start() >= start && e.end() <= end) {
        out.replace(e.start() - start, e.end() - start, e.text());
      }
    }
    return out.toString();
  }

  /**
   * The receiver's edits for one call: each {@code this} of the body becomes the receiver, and each
   * of its members the body names bare gets the receiver before it. None for a call with no
   * receiver or {@code this}. A receiver that is not fixed is refused: the body would evaluate it
   * at each use.
   */
  private static List<TextEdit> receiverEdits(
      MethodInvocation site,
      List<ASTNode> thisUses,
      List<SimpleName> implicit,
      String siteText,
      String name) {
    Expression receiver = site.getExpression();
    if (receiver == null || receiver instanceof ThisExpression) {
      return List.of();
    }
    String text =
        siteText.substring(
            receiver.getStartPosition(), receiver.getStartPosition() + receiver.getLength());
    if (!thisUses.isEmpty() || !implicit.isEmpty()) {
      Precondition.require(
          Precondition.that(
              () -> Effects.of(receiver).fixed(),
              () ->
                  "the receiver "
                      + text
                      + " of a call of "
                      + name
                      + " calls, assigns or reads changing state, and inlined, the body would"
                      + " evaluate it at each use. Put it in a variable first."));
    }
    String as = InlineVariable.needsParentheses(receiver) ? "(" + text + ")" : text;
    List<TextEdit> edits = new ArrayList<>();
    for (ASTNode t : thisUses) {
      edits.add(new TextEdit(t.getStartPosition(), t.getStartPosition() + t.getLength(), as));
    }
    for (SimpleName n : implicit) {
      edits.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), as + "."));
    }
    return edits;
  }

  /**
   * Edits that qualify a bare field of the body that a local or parameter of the calling member
   * hides: {@code this.count}, or {@code Type.COUNT} for a static. Inlined there, the bare name
   * would mean the local. A name {@code done} already rewrites (the receiver's) is left to it.
   */
  private static List<TextEdit> shadowEdits(
      MethodInvocation site, Block body, List<TextEdit> done, String typeName) {
    ASTNode member = site;
    while (member != null && !(member instanceof BodyDeclaration)) {
      member = member.getParent();
    }
    if (member == null) {
      return List.of();
    }
    Set<String> hidden = declaredIn(member);
    List<TextEdit> out = new ArrayList<>();
    body.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (!n.isDeclaration()
                && !qualified(n)
                && hidden.contains(n.getIdentifier())
                && n.resolveBinding() instanceof IVariableBinding v
                && v.isField()
                && done.stream().noneMatch(e -> e.start() == n.getStartPosition())) {
              String prefix = Modifier.isStatic(v.getModifiers()) ? typeName + "." : "this.";
              out.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), prefix));
            }
            return true;
          }
        });
    return out;
  }

  /**
   * The {@code this} expressions of {@code body}, and the names of {@code owner}'s instance members
   * (inherited ones too) it uses bare, outside anonymous and local classes. Refuses {@code super}.
   */
  private static void receiverUses(
      Block body,
      ITypeBinding owner,
      String name,
      List<ASTNode> thisUses,
      List<SimpleName> implicit) {
    body.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(AnonymousClassDeclaration a) {
            return false;
          }

          @Override
          public boolean visit(TypeDeclarationStatement t) {
            return false;
          }

          @Override
          public boolean visit(ThisExpression t) {
            if (t.getQualifier() == null) {
              thisUses.add(t);
            }
            return false;
          }

          @Override
          public boolean visit(SuperMethodInvocation s) {
            throw superRefusal();
          }

          @Override
          public boolean visit(SuperFieldAccess s) {
            throw superRefusal();
          }

          private Refusal superRefusal() {
            return new Refusal(
                "the body of "
                    + name
                    + " uses super, which means "
                    + owner.getName()
                    + "'s superclass only inside it. Keep the method.");
          }

          @Override
          public boolean visit(SimpleName n) {
            if (n.isDeclaration() || qualified(n)) {
              return true;
            }
            IBinding b = n.resolveBinding();
            ITypeBinding declaring =
                b instanceof IVariableBinding v && v.isField()
                    ? v.getDeclaringClass()
                    : b instanceof IMethodBinding mb
                            && n.getParent() instanceof MethodInvocation mi
                            && mi.getName() == n
                        ? mb.getDeclaringClass()
                        : null;
            if (declaring != null
                && !Modifier.isStatic(b.getModifiers())
                && owner.isSubTypeCompatible(declaring.getErasure())) {
              implicit.add(n);
            }
            return true;
          }
        });
  }

  /** Whether {@code n} has a qualifier before it: {@code a.n}, {@code e.n}, {@code e.n()}. */
  private static boolean qualified(SimpleName n) {
    ASTNode p = n.getParent();
    return (p instanceof QualifiedName q && q.getName() == n)
        || (p instanceof FieldAccess f && f.getName() == n)
        || (p instanceof MethodInvocation mi && mi.getName() == n && mi.getExpression() != null);
  }

  /**
   * Refuses a method that a declaration in {@code parsed} overrides: a call might run that one. The
   * method's binding is taken from {@code parsed}'s own parse of {@code file}, since bindings of
   * two parses do not compare.
   */
  private static void refuseOverridden(
      Map<Path, CompilationUnit> parsed, Path file, IMethodBinding method, String name) {
    CompilationUnit home = parsed.get(file);
    if (home == null
        || !(home.findDeclaringNode(method.getKey()) instanceof MethodDeclaration local)
        || local.resolveBinding() == null) {
      return;
    }
    IMethodBinding target = local.resolveBinding();
    for (CompilationUnit u : parsed.values()) {
      u.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration d) {
              IMethodBinding b = d.resolveBinding();
              if (b != null && !b.getKey().equals(target.getKey()) && b.overrides(target)) {
                throw new Refusal(
                    name
                        + " is overridden by "
                        + b.getDeclaringClass().getName()
                        + "."
                        + b.getName()
                        + "(), so a call of it may run that one; inlined, it would not. Keep the"
                        + " method.");
              }
              return true;
            }
          });
    }
  }

  /**
   * The statement whose value the call is all of, directly in a block: {@code T v = m(..);}, {@code
   * return m(..);} or {@code v = m(..);}. Refused otherwise: the body's other statements could not
   * go before it without running before what the statement runs first.
   */
  private static Statement wholeStatement(MethodInvocation site, String name) {
    ASTNode p = site.getParent();
    if (p instanceof VariableDeclarationFragment f
        && f.getInitializer() == site
        && f.getParent() instanceof VariableDeclarationStatement s
        && s.fragments().size() == 1
        && s.getParent() instanceof Block) {
      return s;
    }
    if (p instanceof ReturnStatement r && r.getParent() instanceof Block) {
      return r;
    }
    if (p instanceof Assignment a
        && a.getRightHandSide() == site
        && a.getLeftHandSide() instanceof SimpleName
        && a.getParent() instanceof ExpressionStatement s
        && s.getParent() instanceof Block) {
      return s;
    }
    throw new Refusal(
        "a call of "
            + name
            + ", whose body has several statements, is part of a larger expression or not"
            + " directly in a block, so those statements cannot go before it. Put the call in a"
            + " variable of its own first.");
  }

  /** Refuses a local of the body whose name the member around the call also uses. */
  private static void checkLocals(MethodInvocation site, Set<String> bodyLocals, String name) {
    if (bodyLocals.isEmpty()) {
      return;
    }
    ASTNode n = site;
    while (n != null && !(n instanceof BodyDeclaration)) {
      n = n.getParent();
    }
    if (n == null) {
      return;
    }
    Set<String> used = new HashSet<>();
    n.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName s) {
            used.add(s.getIdentifier());
            return true;
          }
        });
    for (String local : bodyLocals) {
      if (used.contains(local)) {
        throw new Refusal(
            "the body of "
                + name
                + " declares "
                + local
                + ", which the member that calls it also names, so inlined the two would clash."
                + " Rename one of them first.");
      }
    }
  }

  /** Refuses a body that assigns a parameter: in place of the call it would assign the argument. */
  private static void checkNotAssigned(
      List<ParamUse> uses,
      CompilationUnit unit,
      List<SingleVariableDeclaration> params,
      String name) {
    for (ParamUse u : uses) {
      ASTNode n = NodeFinder.perform(unit, u.start(), u.end() - u.start());
      ASTNode p = n.getParent();
      if ((p instanceof Assignment a && a.getLeftHandSide() == n)
          || p instanceof PostfixExpression
          || (p instanceof PrefixExpression pre
              && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                  || pre.getOperator() == PrefixExpression.Operator.DECREMENT))) {
        throw new Refusal(
            name
                + " assigns its parameter "
                + params.get(u.index()).getName().getIdentifier()
                + ", which in place of the call would assign the caller's argument. Assign a local"
                + " in the method instead, or keep it.");
      }
    }
  }

  /** The returns of {@code body}, outside lambdas and local or anonymous classes. */
  private static int returnsIn(Block body) {
    int[] count = {0};
    body.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(ReturnStatement r) {
            count[0]++;
            return true;
          }

          @Override
          public boolean visit(LambdaExpression l) {
            return false;
          }

          @Override
          public boolean visit(AnonymousClassDeclaration a) {
            return false;
          }

          @Override
          public boolean visit(TypeDeclarationStatement t) {
            return false;
          }
        });
    return count[0];
  }

  /** The names of the variables {@code node} declares. */
  private static Set<String> declaredIn(ASTNode node) {
    Set<String> out = new HashSet<>();
    node.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode n) {
            if (n instanceof VariableDeclaration d) {
              out.add(d.getName().getIdentifier());
            }
          }
        });
    return out;
  }

  /** The spaces and tabs that start the line {@code at} is on. */
  private static String indentOf(String text, int at) {
    int start = Lines.start(text, at);
    int end = start;
    while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
      end++;
    }
    return text.substring(start, end);
  }

  /**
   * {@code text} with each line after the first indented by {@code to} in place of {@code from}.
   */
  private static String reindent(String text, String from, String to) {
    return text.replace("\n" + from, "\n" + to);
  }

  /**
   * Refuses arguments that inlining would treat differently: one whose call the body drops, two
   * whose calls would run in another order, and one whose type the call converts.
   */
  private static void checkArguments(
      List<Expression> args,
      List<SingleVariableDeclaration> params,
      int[] useCount,
      String name,
      boolean[] receiverOnly) {
    int effects = 0;
    for (int i = 0; i < args.size(); i++) {
      Expression arg = args.get(i);
      String param = params.get(i).getName().getIdentifier();
      boolean effect = Effects.of(arg).changes();
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
          && !argType.isEqualTo(paramType)
          && !(receiverOnly[i] && subtype(argType, paramType))) {
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

  /** How many of {@code uses} are of parameter {@code index}. */
  private static int useCount(List<ParamUse> uses, int index) {
    int n = 0;
    for (ParamUse u : uses) {
      if (u.index() == index) {
        n++;
      }
    }
    return n;
  }

  /**
   * Whether reference type {@code sub} extends or implements {@code sup}, by qualified name: the
   * two come from different parses.
   */
  private static boolean subtype(ITypeBinding sub, ITypeBinding sup) {
    if (sub.isPrimitive() || sup.isPrimitive()) {
      return false;
    }
    String target = sup.getErasure().getQualifiedName();
    if (target.equals("java.lang.Object")) {
      return true;
    }
    List<ITypeBinding> todo = new ArrayList<>(List.of(sub));
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding t = todo.get(i);
      if (t.getErasure().getQualifiedName().equals(target)) {
        return true;
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return false;
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
    List<ASTNode> effects = Effects.of(bodyNode).normalFlowChanges();
    for (ParamUse u : uses) {
      Expression arg = args.get(u.index());
      if (Effects.of(arg).fixed()) {
        continue;
      }
      String param = params.get(u.index()).getName().getIdentifier();
      String source =
          siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
      ASTNode use = NodeFinder.perform(bodyNode.getRoot(), u.start(), u.end() - u.start());
      String where = deferral(use, bodyNode, Effects.of(arg).changes());
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
      if (first[i] < 0 || Effects.of(args.get(i)).fixed()) {
        continue;
      }
      for (int j = i + 1; j < args.size(); j++) {
        if (first[j] < 0 || Effects.of(args.get(j)).fixed() || first[j] > first[i]) {
          continue;
        }
        if (Effects.of(args.get(i)).changes() || Effects.of(args.get(j)).changes()) {
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
   * runs once, in order. Branches and loops count only for an argument with an effect.
   */
  private static String deferral(ASTNode use, ASTNode bodyNode, boolean effect) {
    String later = Effects.later(use, bodyNode);
    return later != null || !effect ? later : Effects.sometimes(use, bodyNode);
  }

  /** Whether {@code node} names a static field or method that {@code owner} declares. */
  private static boolean usesMembersOf(ASTNode node, ITypeBinding owner) {
    boolean[] found = {false};
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            IBinding b = n.resolveBinding();
            // Instance members get the receiver before them; statics would mean another class's.
            if (b instanceof IVariableBinding v
                && v.isField()
                && Modifier.isStatic(v.getModifiers())
                && owner.isEqualTo(v.getDeclaringClass())) {
              found[0] = true;
            }
            if (b instanceof IMethodBinding m
                && Modifier.isStatic(m.getModifiers())
                && owner.isEqualTo(m.getDeclaringClass())) {
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
