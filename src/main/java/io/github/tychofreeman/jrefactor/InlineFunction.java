package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
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
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;

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
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      throw new Refusal(
          "name is Type.method, the class and the static method to inline, not " + name + ".");
    }
    String typeName = name.substring(0, dot);
    String member = name.substring(dot + 1);
    Path file = project.javaFile(rel).toAbsolutePath().normalize();
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    TypeDeclaration type = MemberMove.typeNamed(unit, typeName);
    if (type == null || type.resolveBinding() == null) {
      throw new Refusal(typeName + " is not a top-level class of " + rel + " that resolves.");
    }
    BodyDeclaration declared = MemberMove.member(type, typeName, member, rel, "inline_function");
    if (!(declared instanceof MethodDeclaration m)) {
      throw new Refusal(name + " is a field. inline_function inlines a method.");
    }
    IMethodBinding method = m.resolveBinding();
    if (method == null) {
      throw new Refusal(name + " does not resolve.");
    }
    if (!Modifier.isStatic(m.getModifiers())) {
      throw new Refusal(
          name
              + " is not static. inline_function inlines static methods in this first version; an"
              + " instance method needs its receiver.");
    }
    if (m.isVarargs() || !m.typeParameters().isEmpty()) {
      throw new Refusal(
          name + " is varargs or generic, which inline_function does not inline yet.");
    }
    if (m.getBody() == null) {
      throw new Refusal(name + " has no body (abstract or native), so there is nothing to inline.");
    }
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

    checkNotAssigned(uses, unit, params, name);
    ITypeBinding owner = type.resolveBinding();
    List<ASTNode> effects = effectsIn(bodyNode);
    List<SimpleName> statics = unqualifiedStatics(bodyNode, owner, name);
    Set<String> bodyNames = declaredIn(bodyNode);
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
                declaringText,
                owner,
                effects,
                statics,
                bodyNames));
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
      String declaringText,
      ITypeBinding owner,
      List<ASTNode> effects,
      List<SimpleName> statics,
      Set<String> bodyNames) {
    List<Expression> args = typed(site.arguments(), Expression.class);
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
    checkArguments(
        args, uses, params, effects, bodyNames, value != null ? value : body, siteText, name);
    String bodyText = declaringText.substring(bodyStart, bodyEnd);
    StringBuilder out = new StringBuilder(bodyText);
    List<TextEdit> rewrites = new ArrayList<>();
    for (ParamUse u : uses) {
      rewrites.add(
          new TextEdit(
              u.start(),
              u.end(),
              argumentText(args.get(u.index()), params.get(u.index()), siteText)));
    }
    // A static of the method's class, named bare, could mean another declaration at the call.
    String typeName = name.substring(0, name.lastIndexOf('.'));
    boolean elsewhere = !inType(site, owner);
    for (SimpleName s : statics) {
      if (elsewhere || declaresLocal(site, s.getIdentifier())) {
        rewrites.add(new TextEdit(s.getStartPosition(), s.getStartPosition(), typeName + "."));
      }
    }
    rewrites.sort((a, b) -> Integer.compare(b.start(), a.start()));
    for (TextEdit r : rewrites) {
      out.replace(r.start() - bodyStart, r.end() - bodyStart, r.text());
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

  /**
   * Refuses arguments the inlined body would run at another time than the call did: one with an
   * effect that the body drops, defers or puts after another effect, one that reads changing state
   * (a field, an array element) after an effect of the body, and such arguments used in another
   * order than they are passed. Also refuses an argument whose names the body declares, and one
   * whose type could pick another overload in the body.
   */
  private static void checkArguments(
      List<Expression> args,
      List<ParamUse> uses,
      List<SingleVariableDeclaration> params,
      List<ASTNode> effects,
      Set<String> bodyNames,
      ASTNode bodyNode,
      String siteText,
      String name) {
    int previous = -1;
    String previousParam = null;
    for (int i = 0; i < args.size(); i++) {
      Expression arg = args.get(i);
      String param = params.get(i).getName().getIdentifier();
      String source =
          siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
      for (String id : namesIn(arg)) {
        if (bodyNames.contains(id)) {
          throw new Refusal(
              "the body of "
                  + name
                  + " declares "
                  + id
                  + ", and the argument "
                  + source
                  + " for "
                  + param
                  + " names "
                  + id
                  + " too, so in the body it would mean the body's. Rename one of them first.");
        }
      }
      List<ParamUse> mine = new ArrayList<>();
      for (ParamUse u : uses) {
        if (u.index() == i) {
          mine.add(u);
        }
      }
      checkOverloads(arg, params.get(i), mine, bodyNode, source, name);
      if (fixed(arg)) {
        continue;
      }
      boolean effect = ExtractVariable.hasEffect(arg);
      if (effect && mine.isEmpty()) {
        throw new Refusal(
            "parameter "
                + param
                + " is not used in the body of "
                + name
                + ", and its argument "
                + source
                + " calls, assigns or changes a variable, so that would no longer run. Put the"
                + " argument in a variable first.");
      }
      for (ParamUse u : mine) {
        ASTNode use = NodeFinder.perform(bodyNode.getRoot(), u.start(), u.end() - u.start());
        String where = deferral(use, bodyNode, effect);
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
      if (!mine.isEmpty()) {
        int first = mine.stream().mapToInt(ParamUse::start).min().getAsInt();
        if (first < previous) {
          throw new Refusal(
              "the arguments for "
                  + previousParam
                  + " and "
                  + param
                  + " of "
                  + name
                  + " call, assign or read changing state, and the body uses them in another"
                  + " order, so they would run in another order. Put them in variables first.");
        }
        previous = first;
        previousParam = param;
      }
    }
  }

  /**
   * Refuses a reference argument of another type than its parameter's when the body passes the
   * parameter to a call: there the argument's type could choose another overload.
   */
  private static void checkOverloads(
      Expression arg,
      SingleVariableDeclaration param,
      List<ParamUse> mine,
      ASTNode bodyNode,
      String source,
      String name) {
    ITypeBinding to = param.getType().resolveBinding();
    ITypeBinding from = arg.resolveTypeBinding();
    if (to == null
        || from == null
        || to.isPrimitive()
        || from.isPrimitive()
        || from.getKey().equals(to.getKey())) {
      return;
    }
    for (ParamUse u : mine) {
      ASTNode use = NodeFinder.perform(bodyNode.getRoot(), u.start(), u.end() - u.start());
      ASTNode parent = use.getParent();
      boolean passed =
          (parent instanceof MethodInvocation mi && mi.arguments().contains(use))
              || (parent instanceof SuperMethodInvocation smi && smi.arguments().contains(use))
              || (parent instanceof ClassInstanceCreation cic && cic.arguments().contains(use));
      if (passed) {
        throw new Refusal(
            "the argument "
                + source
                + " for "
                + param.getName().getIdentifier()
                + " of "
                + name
                + " is a "
                + from.getName()
                + ", not a "
                + to.getName()
                + ", and the body passes it to a call, where another overload could be chosen."
                + " Cast the argument to "
                + to.getName()
                + " first.");
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

  /**
   * The names in {@code node} of static fields and methods it reaches unqualified, which are {@code
   * owner}'s or its supertypes'. One reached by a static import is refused, since a call in another
   * file need not import it.
   */
  private static List<SimpleName> unqualifiedStatics(
      ASTNode node, ITypeBinding owner, String name) {
    List<SimpleName> out = new ArrayList<>();
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName s) {
            IBinding b = s.resolveBinding();
            ITypeBinding declaring;
            if (b instanceof IVariableBinding v
                && v.isField()
                && Modifier.isStatic(v.getModifiers())
                && !qualified(s)) {
              declaring = v.getDeclaringClass();
            } else if (b instanceof IMethodBinding mb
                && Modifier.isStatic(mb.getModifiers())
                && s.getParent() instanceof MethodInvocation mi
                && mi.getName() == s
                && mi.getExpression() == null) {
              declaring = mb.getDeclaringClass();
            } else {
              return true;
            }
            if (declaring == null || !owner.isSubTypeCompatible(declaring.getErasure())) {
              String of = declaring == null ? "another class" : declaring.getName();
              throw new Refusal(
                  "the body of "
                      + name
                      + " uses "
                      + s.getIdentifier()
                      + " of "
                      + of
                      + " by a static import, which a call in another file need not have. Qualify"
                      + " it in the method first ("
                      + of
                      + "."
                      + s.getIdentifier()
                      + ").");
            }
            out.add(s);
            return true;
          }
        });
    return out;
  }

  /** Whether {@code s} is the name part of a qualified name or field access, or a declaration. */
  private static boolean qualified(SimpleName s) {
    ASTNode p = s.getParent();
    return (p instanceof QualifiedName q && q.getName() == s)
        || (p instanceof FieldAccess f && f.getName() == s)
        || p instanceof SuperFieldAccess
        || s.isDeclaration();
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
          || (p instanceof PrefixExpression pre && changes(pre))) {
        throw new Refusal(
            name
                + " assigns its parameter "
                + params.get(u.index()).getName().getIdentifier()
                + ", which in place of the call would assign the caller's argument. Assign a local"
                + " in the method instead, or keep it.");
      }
    }
  }

  /**
   * The text an argument takes in the body: parenthesized where needed, and cast to the parameter's
   * type where a primitive meets another type ({@code 3} for {@code double x} is {@code ((double)
   * 3)}, so {@code x / 2} still divides doubles).
   */
  private static String argumentText(
      Expression arg, SingleVariableDeclaration param, String siteText) {
    String text =
        siteText.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
    if (InlineVariable.needsParentheses(arg)) {
      text = "(" + text + ")";
    }
    ITypeBinding to = param.getType().resolveBinding();
    ITypeBinding from = arg.resolveTypeBinding();
    if (to != null
        && from != null
        && !from.getKey().equals(to.getKey())
        && (to.isPrimitive() || from.isPrimitive())) {
      return "((" + to.getName() + ") " + text + ")";
    }
    return text;
  }

  /** Whether the innermost type around {@code node} is {@code owner}. */
  private static boolean inType(ASTNode node, ITypeBinding owner) {
    for (ASTNode n = node.getParent(); n != null; n = n.getParent()) {
      if (n instanceof AnonymousClassDeclaration) {
        return false;
      }
      if (n instanceof AbstractTypeDeclaration t) {
        return t.resolveBinding() != null && t.resolveBinding().getKey().equals(owner.getKey());
      }
    }
    return false;
  }

  /** Whether the member around {@code site} declares a variable named {@code id}. */
  private static boolean declaresLocal(ASTNode site, String id) {
    ASTNode n = site;
    while (n != null && !(n instanceof BodyDeclaration)) {
      n = n.getParent();
    }
    if (n == null) {
      return false;
    }
    return declaredIn(n).contains(id);
  }

  /** The names of the variables {@code node} declares (locals, parameters, lambda parameters). */
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

  /** The identifiers {@code node} names. */
  private static Set<String> namesIn(ASTNode node) {
    Set<String> out = new HashSet<>();
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName s) {
            out.add(s.getIdentifier());
            return true;
          }
        });
    return out;
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
    } else if (start > 0) {
      // The last member: take the blank line before it instead.
      int before = text.lastIndexOf('\n', start - 2) + 1;
      if (text.substring(before, start - 1).isBlank()) {
        start = before;
      }
    }
    return new TextEdit(start, end, "");
  }
}
