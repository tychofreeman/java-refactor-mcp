package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
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
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * convert_lambda: a lambda, a method reference or an anonymous class of a functional interface,
 * written as another of the three where the meaning is kept: when a receiver would be evaluated
 * once instead of at each call (or the other way), or {@code this} or a bare method name would mean
 * another object, the conversion is refused.
 */
final class ConvertLambda {

  private static final String NOT_ONE_CALL =
      "the lambda's body is not one call that passes its parameters in order (x -> f(x), (a, b)"
          + " -> a.m(b), x -> new T(x)), so no method reference means the same. Keep the lambda.";

  private ConvertLambda() {}

  static Outcome run(SourceProject project, String rel, Integer line, Integer column, String to)
      throws Exception {
    Precondition.require(
        Precondition.that(
            () -> line != null,
            () ->
                "convert_lambda needs line, the 1-based line the lambda, method reference or"
                    + " anonymous class starts on."),
        Precondition.that(
            () -> List.of("lambda", "anonymous", "method_reference").contains(to),
            () -> "to is lambda, anonymous or method_reference: what it becomes."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    List<ASTNode> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode n) {
            boolean kind =
                n instanceof LambdaExpression
                    || n instanceof MethodReference
                    || (n instanceof ClassInstanceCreation c
                        && c.getAnonymousClassDeclaration() != null);
            if (kind
                && unit.getLineNumber(n.getStartPosition()) == line
                && (column == null || unit.getColumnNumber(n.getStartPosition()) + 1 == column)) {
              found.add(n);
            }
          }
        });
    if (found.isEmpty()) {
      throw new Refusal(
          "no lambda, method reference or anonymous class starts on line "
              + line
              + (column == null ? "" : " at column " + column)
              + " of "
              + rel
              + ". Pass the line (and column) where it starts.");
    }
    if (found.size() > 1) {
      List<String> columns = new ArrayList<>();
      for (ASTNode n : found) {
        columns.add(String.valueOf(unit.getColumnNumber(n.getStartPosition()) + 1));
      }
      throw new Refusal(
          "several start on line "
              + line
              + " of "
              + rel
              + ", at columns "
              + String.join(", ", columns)
              + ". Pass column.");
    }
    ASTNode node = found.get(0);
    String what =
        node instanceof LambdaExpression
            ? "lambda"
            : node instanceof MethodReference ? "method reference" : "anonymous class";
    String replacement =
        switch (to) {
          case "method_reference" -> {
            if (!(node instanceof LambdaExpression l)) {
              throw new Refusal("the " + what + " is not a lambda; convert it to a lambda first.");
            }
            yield reference(l, text);
          }
          case "lambda" -> {
            if (node instanceof MethodReference r) {
              yield lambdaOf(r, text);
            }
            if (node instanceof ClassInstanceCreation c) {
              yield lambdaOf(c, text);
            }
            throw new Refusal("it is a lambda already.");
          }
          default -> {
            if (!(node instanceof LambdaExpression l)) {
              throw new Refusal("the " + what + " is not a lambda; convert it to a lambda first.");
            }
            yield anonymousOf(l, text);
          }
        };
    NameCheck.require(
        project,
        Map.of(opened.file(), text),
        Map.of(
            opened.file(),
            List.of(
                new TextEdit(
                    node.getStartPosition(),
                    node.getStartPosition() + node.getLength(),
                    replacement))),
        NameCheck.meanings(List.of(node)),
        "converting the " + what + " at line " + line);
    String after =
        text.substring(0, node.getStartPosition())
            + replacement
            + text.substring(node.getStartPosition() + node.getLength());
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            after,
            List.of(),
            "converting the " + what + " at line " + line,
            "Nothing was changed: the new form does not compile there (an ambiguous overload,"
                + " say). Keep it as it is.");
    return new Outcome(
        "converted the "
            + what
            + " at line "
            + line
            + " of "
            + rel
            + " to "
            + (!replacement.contains("\n")
                ? replacement
                : to.equals("lambda") ? "a lambda" : "an anonymous class")
            + written.fixed().said(),
        written.declared());
  }

  /** The method reference that means what {@code l} does, or a refusal. */
  private static String reference(LambdaExpression l, String text) {
    List<String> params = new ArrayList<>();
    for (Object o : l.parameters()) {
      params.add(((VariableDeclaration) o).getName().getIdentifier());
    }
    Expression body = l.getBody() instanceof Expression e ? e : single((Block) l.getBody());
    if (body instanceof ClassInstanceCreation c
        && c.getAnonymousClassDeclaration() == null
        && c.getExpression() == null
        && c.typeArguments().isEmpty()
        && params.equals(names(c.arguments()))) {
      return source(text, c.getType()) + "::new";
    }
    if (body instanceof SuperMethodInvocation s
        && s.getQualifier() == null
        && params.equals(names(s.arguments()))) {
      return "super::" + s.getName().getIdentifier();
    }
    if (!(body instanceof MethodInvocation mi)
        || !mi.typeArguments().isEmpty()
        || mi.resolveMethodBinding() == null) {
      throw new Refusal(NOT_ONE_CALL);
    }
    IMethodBinding mb = mi.resolveMethodBinding();
    boolean isStatic = Modifier.isStatic(mb.getModifiers());
    String m = mi.getName().getIdentifier();
    List<String> args = names(mi.arguments());
    Expression receiver = mi.getExpression();
    if (receiver == null && params.equals(args)) {
      return isStatic
          ? Extract.typeName(mb.getDeclaringClass().getErasure()) + "::" + m
          : "this::" + m;
    }
    if (receiver instanceof ThisExpression t && t.getQualifier() == null && params.equals(args)) {
      return "this::" + m;
    }
    if (receiver instanceof SimpleName first
        && !isStatic
        && !params.isEmpty()
        && first.getIdentifier().equals(params.get(0))
        && args != null
        && args.equals(params.subList(1, params.size()))) {
      return Extract.typeName(first.resolveTypeBinding()) + "::" + m;
    }
    if (receiver instanceof Name n && params.equals(args)) {
      IBinding b = n.resolveBinding();
      if (b instanceof ITypeBinding) {
        return source(text, n) + "::" + m;
      }
      if (b instanceof IVariableBinding v && !params.contains(n.getFullyQualifiedName())) {
        if (!fixed(v)) {
          throw new Refusal(
              source(text, n)
                  + " is a field that may change, and a method reference evaluates its receiver"
                  + " once, where the lambda reads it at each call. Keep the lambda.");
        }
        return source(text, n) + "::" + m;
      }
    }
    throw new Refusal(NOT_ONE_CALL);
  }

  /** The lambda that means what {@code r} does, or a refusal. */
  private static String lambdaOf(MethodReference r, String text) {
    ITypeBinding fi = r.resolveTypeBinding();
    IMethodBinding sam = fi == null ? null : fi.getFunctionalInterfaceMethod();
    IMethodBinding target = r.resolveMethodBinding();
    if (sam == null || target == null || !r.typeArguments().isEmpty()) {
      throw new Refusal(
          "the method reference does not resolve to one method of a functional interface"
              + " (or has type arguments), so its lambda cannot be written. Keep it.");
    }
    List<String> names = parameterNames(sam, r);
    String ps = "(" + String.join(", ", names) + ")";
    String all = String.join(", ", names);
    String rest = String.join(", ", names.subList(Math.min(1, names.size()), names.size()));
    String m = target.getName();
    boolean isStatic = Modifier.isStatic(target.getModifiers());
    switch (r) {
      case CreationReference c -> {
        ITypeBinding t = c.getType().resolveBinding();
        if (t != null && t.isArray()) {
          return ps
              + " -> new "
              + Extract.typeName(t.getElementType())
              + "["
              + names.get(0)
              + "]"
              + "[]".repeat(t.getDimensions() - 1);
        }
        return ps + " -> new " + source(text, c.getType()) + "(" + all + ")";
      }
      case SuperMethodReference s -> {
        return ps + " -> super." + m + "(" + all + ")";
      }
      case TypeMethodReference t -> {
        return isStatic
            ? ps + " -> " + source(text, t.getType()) + "." + m + "(" + all + ")"
            : ps + " -> " + names.get(0) + "." + m + "(" + rest + ")";
      }
      case ExpressionMethodReference e -> {
        Expression on = e.getExpression();
        if (on instanceof Name n && n.resolveBinding() instanceof ITypeBinding) {
          return isStatic
              ? ps + " -> " + source(text, n) + "." + m + "(" + all + ")"
              : ps + " -> " + names.get(0) + "." + m + "(" + rest + ")";
        }
        if (on instanceof ThisExpression) {
          return ps + " -> " + source(text, on) + "." + m + "(" + all + ")";
        }
        if (!(on instanceof Name n
            && n.resolveBinding() instanceof IVariableBinding v
            && fixed(v))) {
          throw new Refusal(
              "the receiver "
                  + source(text, on)
                  + " is evaluated once by the method reference, and would be at each call by a"
                  + " lambda. Put it in a variable first.");
        }
        return ps + " -> " + source(text, on) + "." + m + "(" + all + ")";
      }
      default -> throw new Refusal("this kind of method reference is not converted yet.");
    }
  }

  /** The lambda of an anonymous class of a functional interface with its one method, or refused. */
  private static String lambdaOf(ClassInstanceCreation c, String text) {
    AnonymousClassDeclaration a = c.getAnonymousClassDeclaration();
    ITypeBinding t = c.getType().resolveBinding();
    Precondition.require(
        Precondition.that(
            () -> t != null && t.isInterface() && t.getFunctionalInterfaceMethod() != null,
            () ->
                "the anonymous class is not of a functional interface, so it cannot be a lambda."
                    + " Keep it."),
        Precondition.that(
            () ->
                a.bodyDeclarations().size() == 1
                    && a.bodyDeclarations().get(0) instanceof MethodDeclaration m
                    && m.getBody() != null,
            () ->
                "the anonymous class declares more than its one method (a field, another"
                    + " method), which a lambda cannot hold. Keep it."));
    MethodDeclaration m = (MethodDeclaration) a.bodyDeclarations().get(0);
    refuseSelf(m.getBody(), t, "anonymous class", "a lambda");
    List<String> params = new ArrayList<>();
    for (Object o : m.parameters()) {
      params.add(((VariableDeclaration) o).getName().getIdentifier());
    }
    int shift =
        Lines.indentOf(text, m.getStartPosition()).length()
            - Lines.indentOf(text, c.getStartPosition()).length();
    String body = source(text, m.getBody());
    if (shift > 0) {
      body = body.replace("\n" + " ".repeat(shift), "\n");
    }
    return "(" + String.join(", ", params) + ") -> " + body;
  }

  /** The anonymous class that means what {@code l} does, or a refusal. */
  private static String anonymousOf(LambdaExpression l, String text) {
    ITypeBinding fi = l.resolveTypeBinding();
    IMethodBinding sam = fi == null ? null : fi.getFunctionalInterfaceMethod();
    IMethodBinding lb = l.resolveMethodBinding();
    if (sam == null || lb == null) {
      throw new Refusal("the lambda's type does not resolve, so its class cannot be written.");
    }
    refuseSelf(l.getBody(), fi, "lambda", "an anonymous class");
    String ind = Lines.indentOf(text, l.getStartPosition());
    List<String> params = new ArrayList<>();
    for (int i = 0; i < l.parameters().size(); i++) {
      VariableDeclaration p = (VariableDeclaration) l.parameters().get(i);
      params.add(Extract.typeName(lb.getParameterTypes()[i]) + " " + p.getName().getIdentifier());
    }
    String result = Extract.typeName(lb.getReturnType());
    List<String> thrown = new ArrayList<>();
    for (ITypeBinding e : sam.getExceptionTypes()) {
      thrown.add(Extract.typeName(e));
    }
    String body;
    if (l.getBody() instanceof Block b) {
      body = source(text, b).replace("\n", "\n  ");
    } else {
      String expr = source(text, l.getBody());
      body =
          "{\n"
              + ind
              + "    "
              + (result.equals("void") ? "" : "return ")
              + expr
              + ";\n"
              + ind
              + "  }";
    }
    // A wildcard in the target type takes its bound: an anonymous class names one type.
    return "new "
        + Extract.groundTypeName(fi)
        + "() {\n"
        + ind
        + "  @Override\n"
        + ind
        + "  public "
        + result
        + " "
        + sam.getName()
        + "("
        + String.join(", ", params)
        + ")"
        + (thrown.isEmpty() ? "" : " throws " + String.join(", ", thrown))
        + " "
        + body
        + "\n"
        + ind
        + "}";
  }

  /**
   * Refuses {@code this}, and a bare call of a method of {@code type} or of Object, in {@code
   * body}: they mean the {@code from}'s object, and in {@code to} would mean another.
   */
  private static void refuseSelf(ASTNode body, ITypeBinding type, String from, String to) {
    Set<String> own = new HashSet<>(List.of("toString", "equals", "hashCode", "getClass"));
    // Its own methods and every supertype's: an inherited default means the object too.
    for (ITypeBinding t : Hierarchy.supertypes(type, true)) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        own.add(m.getName());
      }
    }
    body.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(AnonymousClassDeclaration a) {
            return false;
          }

          @Override
          public boolean visit(ThisExpression t) {
            if (t.getQualifier() == null) {
              throw new Refusal(
                  "the "
                      + from
                      + " uses this, which in "
                      + to
                      + " would mean another object. Keep it.");
            }
            return false;
          }

          @Override
          public boolean visit(SuperMethodInvocation s) {
            return refuseSuper(s.getQualifier());
          }

          @Override
          public boolean visit(SuperFieldAccess s) {
            return refuseSuper(s.getQualifier());
          }

          @Override
          public boolean visit(SuperMethodReference s) {
            return refuseSuper(s.getQualifier());
          }

          private boolean refuseSuper(Name qualifier) {
            if (qualifier == null) {
              throw new Refusal(
                  "the "
                      + from
                      + " uses super, which in "
                      + to
                      + " would mean another class's superclass. Keep it.");
            }
            return true;
          }

          @Override
          public boolean visit(MethodInvocation mi) {
            if (mi.getExpression() == null && own.contains(mi.getName().getIdentifier())) {
              throw new Refusal(
                  "the "
                      + from
                      + " calls "
                      + mi.getName()
                      + "() bare, which in "
                      + to
                      + " would mean another object's. Qualify it first, or keep it.");
            }
            return true;
          }
        });
  }

  /** Whether a method reference's receiver {@code v} holds the same value at each call. */
  private static boolean fixed(IVariableBinding v) {
    int mods = v.getModifiers();
    return v.isField()
        ? Modifier.isStatic(mods) && Modifier.isFinal(mods)
        : Modifier.isFinal(mods) || v.isEffectivelyFinal();
  }

  /** The one expression of a block that is one return or one expression statement, or null. */
  private static Expression single(Block b) {
    if (b.statements().size() != 1) {
      return null;
    }
    Object s = b.statements().get(0);
    return s instanceof ReturnStatement r
        ? r.getExpression()
        : s instanceof ExpressionStatement e ? e.getExpression() : null;
  }

  /** The identifiers of {@code expressions} when each is a bare name, else null. */
  private static List<String> names(List<?> expressions) {
    List<String> out = new ArrayList<>();
    for (Object o : expressions) {
      if (!(o instanceof SimpleName n)) {
        return null;
      }
      out.add(n.getIdentifier());
    }
    return out;
  }

  /**
   * Names for a lambda's parameters, one per parameter of {@code sam}: the first letter of each
   * type's name, made unique and unused in the member around {@code at}.
   */
  private static List<String> parameterNames(IMethodBinding sam, ASTNode at) {
    Set<String> used = new HashSet<>();
    ASTNode member = at;
    while (member != null && !(member instanceof BodyDeclaration)) {
      member = member.getParent();
    }
    if (member != null) {
      for (SimpleName n : Nodes.all(member, SimpleName.class)) {
        used.add(n.getIdentifier());
      }
    }
    List<String> out = new ArrayList<>();
    for (ITypeBinding t : sam.getParameterTypes()) {
      ITypeBinding base = t.isArray() ? t.getElementType() : t.getErasure();
      String first = base.getName().substring(0, 1).toLowerCase();
      String name = first;
      for (int i = 2;
          used.contains(name) || out.contains(name) || SourceVersion.isKeyword(name);
          i++) {
        name = first + i;
      }
      out.add(name);
    }
    return out;
  }

  private static String source(String text, ASTNode n) {
    return text.substring(n.getStartPosition(), n.getStartPosition() + n.getLength());
  }
}
