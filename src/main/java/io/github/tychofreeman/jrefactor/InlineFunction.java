package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
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
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
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

  /**
   * A use of a parameter in the body: where it is, which parameter, and whether it stands alone (a
   * whole argument, return value, initializer or right-hand side), so its argument needs no
   * parentheses there.
   */
  private record ParamUse(int start, int end, int index, boolean alone) {}

  static Outcome run(SourceProject project, String rel, String name) throws Exception {
    Qualified q = Qualified.of(name, "Type.method, the class and the static method to inline");
    String typeName = q.type();
    String member = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    AbstractTypeDeclaration type = opened.classOrRecord(typeName);
    BodyDeclaration declared = MemberMove.member(type, typeName, member, rel, "inline");
    Precondition.require(
        Precondition.that(
            () -> declared instanceof MethodDeclaration,
            () ->
                name
                    + " is a field. inline takes a method as Type.method, or a local variable with line."));
    MethodDeclaration m = (MethodDeclaration) declared;
    IMethodBinding method = m.resolveBinding();
    Precondition.require(
        Precondition.that(() -> method != null, () -> name + " does not resolve."),
        Precondition.that(
            () -> !m.isVarargs() && m.typeParameters().isEmpty(),
            () -> name + " is varargs or generic, which inline does not inline yet."),
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
    Holding holding = Holding.of(project, member, file);
    Map<Path, String> texts = holding.texts();
    Map<Path, CompilationUnit> parsed = holding.units();

    List<SingleVariableDeclaration> params = typed(m.parameters(), SingleVariableDeclaration.class);
    List<IVariableBinding> paramBindings = new ArrayList<>();
    for (SingleVariableDeclaration p : params) {
      paramBindings.add(p.resolveBinding());
    }
    ASTNode bodyNode = value != null ? value : body;
    int bodyStart = value != null ? value.getStartPosition() : statements.get(0).getStartPosition();
    int bodyEnd = Nodes.end(bodyNode);
    List<ParamUse> uses = paramUsesIn(m.getBody(), paramBindings);
    checkNotAssigned(uses, unit, params, name);
    // A parameter used only as a call's receiver dispatches on the argument's own type, and one
    // passed on to a method with no overload resolves to it still, so a subtype argument means
    // the same there.
    boolean[] subtypeSafe = new boolean[params.size()];
    for (int i = 0; i < params.size(); i++) {
      subtypeSafe[i] = useCount(uses, i) > 0;
    }
    for (ParamUse u : uses) {
      ASTNode n = NodeFinder.perform(unit, u.start(), u.end() - u.start());
      if (!(n.getParent() instanceof MethodInvocation mi && mi.getExpression() == n)
          && !soleOverloadArgument(n)) {
        subtypeSafe[u.index()] = false;
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
              if (b != null && Bindings.same(b, method)) {
                sites.add(node);
              }
              return true;
            }

            @Override
            public boolean preVisit2(ASTNode node) {
              if (node instanceof MethodReference r) {
                IMethodBinding b = r.resolveMethodBinding();
                if (b != null && Bindings.same(b, method)) {
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
                + ", which inline cannot rewrite. Change it to a call first.");
      }
      if (sites.isEmpty()) {
        continue;
      }
      List<TextEdit> list = new ArrayList<>();
      for (MethodInvocation site : sites) {
        if (usesOwnMembers && !(p.equals(file) && Nodes.within(site, type))) {
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
          nullable.add(Nodes.show(receiver) + " in " + project.relative(p));
        }
        list.addAll(
            replacementFor(
                project,
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
                subtypeSafe));
        calls++;
      }
      edits.computeIfAbsent(p, k -> new ArrayList<>()).addAll(list);
    }
    if (calls == 0) {
      throw new Refusal(
          name + " has no calls in the project. Delete it with replace_declaration instead.");
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
    edits
        .computeIfAbsent(declaringFile, k -> new ArrayList<>())
        .add(MemberMove.removal(declaringText, m));

    NameCheck.require(project, texts, edits, NameCheck.meanings(fragments), "inlining " + name);
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            texts,
            edits,
            List.of(),
            "inlining " + name,
            "Keep the method, or change its callers first.");
    Stubs.Declared declaredStubs = written.declared();
    return new Outcome(
        "inlined "
            + name
            + " into "
            + calls
            + (calls == 1 ? " call" : " calls")
            + " and deleted the method in "
            + rel
            + written.said(),
        declaredStubs);
  }

  /**
   * The edits that replace one call with the body, its parameters given the call's arguments: the
   * call itself, and for a body of several statements, those before the last put before the call's
   * statement.
   */
  private static List<TextEdit> replacementFor(
      SourceProject project,
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
      boolean[] subtypeSafe) {
    List<Expression> args = typed(site.arguments(), Expression.class);
    checkArguments(args, params, useCount, name, subtypeSafe);
    // Each argument ran just before the body; inlined in place, it runs at its parameter's uses.
    // One the gate refuses there is bound to a local before the call's statement instead.
    boolean[] bind = new boolean[args.size()];
    Refusal inPlace = null;
    for (int i = 0; i < args.size(); i++) {
      List<ASTNode> at = new ArrayList<>();
      for (ParamUse u : uses) {
        if (u.index() == i) {
          at.add(NodeFinder.perform(whole.getRoot(), u.start(), u.end() - u.start()));
        }
      }
      Behavior.Move move =
          new Behavior.Move(
              args.get(i), whole, at, label(args, params, i, siteText), "inlining " + name);
      Refusal r = Exceptional.trial(project, () -> Behavior.flow(project, move));
      if (r != null) {
        bind[i] = true;
        inPlace = inPlace != null ? inPlace : r;
      }
    }
    try {
      checkOrder(args, uses, params, name);
    } catch (Refusal r) {
      inPlace = inPlace != null ? inPlace : r;
      Arrays.fill(bind, true);
    }
    String[] local = bindings(project, site, whole, args, params, bind, siteText, name, inPlace);
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
          substituted(declaringText, bodyStart, bodyEnd, uses, args, local, siteText, receiver);
      // A call that is all of an initializer, a return or an assignment's value needs none.
      boolean entire =
          site.getParent() instanceof VariableDeclarationFragment
              || site.getParent() instanceof ReturnStatement
              || (site.getParent() instanceof Assignment a && a.getRightHandSide() == site);
      if (!entire && InlineVariable.needsParentheses(value)) {
        replaced = "(" + replaced + ")";
      }
      TextEdit call = new TextEdit(site.getStartPosition(), Nodes.end(site), replaced);
      if (prefix.isEmpty() && !any(local)) {
        return List.of(call);
      }
      Statement statement = prefix.isEmpty() ? blockStatement(site) : wholeStatement(site, name);
      int at = statement.getStartPosition();
      String declared =
          declarations(args, params, local, siteText, Lines.indentOf(siteText, at), useCount);
      if (prefix.isEmpty()) {
        return List.of(new TextEdit(at, at, declared), call);
      }
      int from = prefix.get(0).getStartPosition();
      Statement end = prefix.get(prefix.size() - 1);
      String before =
          reindent(
              substituted(
                  declaringText, from, Nodes.end(end), uses, args, local, siteText, receiver),
              Lines.indentOf(declaringText, from),
              Lines.indentOf(siteText, statement.getStartPosition()),
              textBlocks(whole, declaringText));
      return List.of(
          new TextEdit(at, at, declared + before + "\n" + Lines.indentOf(siteText, at)), call);
    }
    if (!(site.getParent() instanceof ExpressionStatement stmt)) {
      throw new Refusal(name + " returns nothing, so a call of it can only be a statement.");
    }
    String replaced =
        declarations(
                args,
                params,
                local,
                siteText,
                Lines.indentOf(siteText, stmt.getStartPosition()),
                useCount)
            + reindent(
                substituted(
                    declaringText, bodyStart, bodyEnd, uses, args, local, siteText, receiver),
                Lines.indentOf(declaringText, bodyStart),
                Lines.indentOf(siteText, stmt.getStartPosition()),
                textBlocks(whole, declaringText));
    return List.of(new TextEdit(stmt.getStartPosition(), Nodes.end(stmt), replaced));
  }

  /** "the argument next() for x": argument {@code i} in a refusal. */
  private static String label(
      List<Expression> args, List<SingleVariableDeclaration> params, int i, String siteText) {
    Expression arg = args.get(i);
    return "the argument "
        + Nodes.source(siteText, arg)
        + " for "
        + params.get(i).getName().getIdentifier();
  }

  /**
   * The local each argument marked in {@code bind} is bound to, by index (null for one inlined in
   * place). Every argument before the last bound one that is not fixed is bound too, so the
   * arguments run once, in order, before the statement of the call, as they ran before the body.
   * Moving them there is an extract, checked by the behavior gate; when that is refused, so is the
   * call, with {@code inPlace}, the reason inlining in place was.
   */
  private static String[] bindings(
      SourceProject project,
      MethodInvocation site,
      Block whole,
      List<Expression> args,
      List<SingleVariableDeclaration> params,
      boolean[] bind,
      String siteText,
      String name,
      Refusal inPlace) {
    String[] local = new String[args.size()];
    int last = -1;
    for (int i = 0; i < bind.length; i++) {
      if (bind[i]) {
        last = i;
      }
    }
    if (last < 0) {
      return local;
    }
    for (int i = 0; i < last; i++) {
      bind[i] |= !Effects.of(args.get(i)).fixed();
    }
    Statement statement;
    try {
      statement = blockStatement(site);
      int first = 0;
      while (!bind[first]) {
        first++;
      }
      Behavior.flow(
          project,
          new Behavior.Move(
              args.get(first),
              statement,
              List.of(statement),
              label(args, params, first, siteText),
              "binding it to a local before the call"));
    } catch (Refusal r) {
      throw inPlace;
    }
    ASTNode decl = Nodes.ancestorOrSelf(site, BodyDeclaration.class);
    Set<String> taken = new HashSet<>();
    for (SimpleName n : Nodes.all(decl, SimpleName.class)) {
      taken.add(n.getIdentifier());
    }
    Set<String> paramNames = new HashSet<>();
    for (SingleVariableDeclaration p : params) {
      paramNames.add(p.getName().getIdentifier());
    }
    for (SimpleName n : Nodes.all(whole, SimpleName.class)) {
      if (!paramNames.contains(n.getIdentifier())) {
        taken.add(n.getIdentifier());
      }
    }
    for (int i = 0; i < args.size(); i++) {
      if (!bind[i]) {
        continue;
      }
      String base = params.get(i).getName().getIdentifier();
      String chosen = base;
      for (int n = 2; taken.contains(chosen); n++) {
        chosen = base + n;
      }
      taken.add(chosen);
      local[i] = chosen;
    }
    return local;
  }

  /** "int v = nextSlot();" and a line break at {@code indent}, for each bound argument in order. */
  private static String declarations(
      List<Expression> args,
      List<SingleVariableDeclaration> params,
      String[] local,
      String siteText,
      String indent,
      int[] useCount) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < args.size(); i++) {
      if (local[i] != null && useCount[i] == 0 && statementExpression(args.get(i))) {
        // The body does not use it: the argument runs for its effect alone.
        Expression arg = args.get(i);
        out.append(siteText, arg.getStartPosition(), Nodes.end(arg)).append(";\n").append(indent);
      } else if (local[i] != null) {
        Expression arg = args.get(i);
        out.append(Extract.typeName(params.get(i).resolveBinding().getType()))
            .append(' ')
            .append(local[i])
            .append(" = ")
            .append(siteText, arg.getStartPosition(), Nodes.end(arg))
            .append(";\n")
            .append(indent);
      }
    }
    return out.toString();
  }

  /**
   * Whether {@code e} can stand as a statement: a call, an assignment, ++ or --, or a new object.
   */
  private static boolean statementExpression(Expression e) {
    return e instanceof MethodInvocation
        || e instanceof SuperMethodInvocation
        || e instanceof Assignment
        || e instanceof ClassInstanceCreation
        || e instanceof PostfixExpression
        || (e instanceof PrefixExpression p
            && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                || p.getOperator() == PrefixExpression.Operator.DECREMENT));
  }

  private static boolean any(String[] local) {
    for (String l : local) {
      if (l != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * The text of {@code start}-{@code end} of the declaring file, each parameter use given its
   * argument, or the local it is bound to ({@code local}, by index), and the receiver's edits
   * ({@code receiver}, at positions of the declaring file) made.
   */
  private static String substituted(
      String declaringText,
      int start,
      int end,
      List<ParamUse> uses,
      List<Expression> args,
      String[] local,
      String siteText,
      List<TextEdit> receiver) {
    List<TextEdit> all = new ArrayList<>(receiver);
    for (ParamUse u : uses) {
      Expression arg = args.get(u.index());
      String argText = Nodes.source(siteText, arg);
      if (local[u.index()] != null) {
        argText = local[u.index()];
      } else if (!u.alone() && InlineVariable.needsParentheses(arg)) {
        argText = "(" + argText + ")";
      }
      all.add(new TextEdit(u.start(), u.end(), argText));
    }
    all.removeIf(e -> e.start() < start || e.end() > end);
    return TextEdit.apply(declaringText.substring(start, end), all, start);
  }

  /**
   * Whether {@code n} is a whole argument of a call, a whole return value, initializer or
   * right-hand side: no operator around it binds into what replaces it.
   */
  private static boolean standsAlone(ASTNode n) {
    ASTNode p = n.getParent();
    return (p instanceof MethodInvocation m && m.arguments().contains(n))
        || (p instanceof ClassInstanceCreation c && c.arguments().contains(n))
        || (p instanceof SuperMethodInvocation s && s.arguments().contains(n))
        || p instanceof ReturnStatement
        || (p instanceof VariableDeclarationFragment f && f.getInitializer() == n)
        || (p instanceof Assignment a && a.getRightHandSide() == n);
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
    String text = Nodes.source(siteText, receiver);
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
      edits.add(new TextEdit(t.getStartPosition(), Nodes.end(t), as));
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
    ASTNode member = Nodes.ancestorOrSelf(site, BodyDeclaration.class);
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
                && !Nodes.qualified(n)
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

          @Override
          public boolean visit(SuperMethodReference s) {
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
            if (n.isDeclaration() || Nodes.qualified(n)) {
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
              if (b != null && !Bindings.same(b, target) && b.overrides(target)) {
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

  /**
   * The statement that holds the call, which must be directly in a block, so that locals can be
   * declared before it.
   */
  private static Statement blockStatement(MethodInvocation site) {
    Statement n = Nodes.ancestorOrSelf(site, Statement.class);
    if (!(n.getParent() instanceof Block)) {
      throw new Refusal(
          "the statement that holds the call is not directly in a block. Put it in braces first.");
    }
    return n;
  }

  /** Refuses a local of the body whose name the member around the call also uses. */
  private static void checkLocals(MethodInvocation site, Set<String> bodyLocals, String name) {
    if (bodyLocals.isEmpty()) {
      return;
    }
    ASTNode n = Nodes.ancestorOrSelf(site, BodyDeclaration.class);
    if (n == null) {
      return;
    }
    Set<String> used = new HashSet<>();
    for (SimpleName s : Nodes.all(n, SimpleName.class)) {
      used.add(s.getIdentifier());
    }
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
      if (n instanceof Expression x && Nodes.written(x)) {
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

  /**
   * {@code text} with each line after the first indented by {@code to} in place of {@code from},
   * but for the lines inside the text blocks {@code blocks} (their source, in order), which keep
   * their indentation so that each string keeps its value.
   */
  private static String reindent(String text, String from, String to, List<String> blocks) {
    List<int[]> inside = new ArrayList<>();
    int at = 0;
    for (String b : blocks) {
      int start = text.indexOf(b, at);
      if (start >= 0) {
        inside.add(new int[] {start, start + b.length()});
        at = start + b.length();
      }
    }
    StringBuilder out = new StringBuilder();
    int line = 0;
    for (int next = text.indexOf('\n'); next >= 0; next = text.indexOf('\n', line)) {
      out.append(text, line, next + 1);
      line = next + 1;
      int lineAt = line;
      if (text.startsWith(from, line)
          && inside.stream().noneMatch(r -> lineAt > r[0] && lineAt < r[1])) {
        out.append(to);
        line += from.length();
      }
    }
    return out.append(text.substring(line)).toString();
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
      boolean[] subtypeSafe) {
    for (int i = 0; i < args.size(); i++) {
      Expression arg = args.get(i);
      String param = params.get(i).getName().getIdentifier();
      ITypeBinding argType = arg.resolveTypeBinding();
      ITypeBinding paramType = params.get(i).resolveBinding().getType();
      if (argType != null
          && !argType.isNullType()
          && paramType != null
          && !argType.isEqualTo(paramType)
          && !(subtypeSafe[i] && Hierarchy.isSubtype(argType, paramType))) {
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
   * Whether {@code node} names a static field or method that {@code owner} declares by its simple
   * name; a qualified use ({@code Keys.declared(b)}) means the same in another class.
   */
  private static boolean usesMembersOf(ASTNode node, ITypeBinding owner) {
    boolean[] found = {false};
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (Nodes.qualified(n)) {
              return true;
            }
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
      if (other != site && Nodes.within(other, site)) {
        throw new Refusal(
            "a call of "
                + name
                + " is inside another call of it. Inline the inner call first, in a batch, or name"
                + " the inner result in a variable.");
      }
    }
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
                  uses.add(new ParamUse(n.getStartPosition(), Nodes.end(n), i, standsAlone(n)));
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
                if (b != null && Bindings.same(b, method)) {
                  found[0] = true;
                }
                return true;
              }
            });
    return found[0];
  }

  /**
   * Whether {@code n} is an argument of a call that an argument of a subtype resolves to the same
   * method: one neither generic nor varargs, and the only method of its name and number of
   * parameters in its class and supertypes.
   */
  private static boolean soleOverloadArgument(ASTNode n) {
    if (!(n.getParent() instanceof MethodInvocation mi) || !mi.arguments().contains(n)) {
      return false;
    }
    IMethodBinding b = mi.resolveMethodBinding();
    if (b == null || b.isVarargs() || b.isGenericMethod() || b.isParameterizedMethod()) {
      return false;
    }
    IMethodBinding decl = b.getMethodDeclaration();
    int same = 0;
    for (ITypeBinding t : Hierarchy.supertypes(decl.getDeclaringClass(), true)) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (m.getName().equals(decl.getName())
            && m.getParameterTypes().length == decl.getParameterTypes().length) {
          same++;
        }
      }
    }
    return same == 1;
  }

  /** The source of each text block in {@code body}, in order. */
  private static List<String> textBlocks(Block body, String text) {
    List<String> out = new ArrayList<>();
    for (TextBlock b : Nodes.all(body, TextBlock.class)) {
      out.add(Nodes.source(text, b));
    }
    return out;
  }
}
