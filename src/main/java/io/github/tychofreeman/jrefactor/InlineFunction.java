package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
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
