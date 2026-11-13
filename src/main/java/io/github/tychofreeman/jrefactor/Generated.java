package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import tools.jackson.databind.ObjectMapper;

final class Generated {
  private Generated() {}

  private static final String ON = "skeleton-only creation is on: ";

  /**
   * Refuses, under skeleton-only creation, typed members that a command writes: a constructor that
   * only assigns its parameters to fields of their names ({@code constructor}), and an override
   * that only throws "not implemented" or only calls the supertype's method ({@code implement}).
   * {@code instead} ends the refusal: where to send the command.
   */
  static void checkMembers(List<?> members, String instead) {
    for (Object d : members) {
      if (!(d instanceof MethodDeclaration m) || m.getBody() == null) {
        continue;
      }
      if (m.isConstructor() && onlyAssignsParameters(m)) {
        List<String> names = new ArrayList<>();
        for (Object o : m.parameters()) {
          names.add("\"" + ((SingleVariableDeclaration) o).getName().getIdentifier() + "\"");
        }
        throw new Refusal(
            ON
                + "a constructor that only assigns its parameters to fields is what"
                + " add_declaration with constructor writes. Send constructor: ["
                + String.join(", ", names)
                + "]"
                + instead);
      }
      if (overrides(m) && (throwsNotImplemented(m) || onlyCallsSuper(m))) {
        throw new Refusal(
            ON
                + "an override that only calls the supertype's method, or only throws not"
                + " implemented, is what add_declaration with implement writes. Send implement: \""
                + m.getName().getIdentifier()
                + "\" (or \"*\" for every abstract one)"
                + instead);
      }
    }
  }

  /**
   * Refuses, under skeleton-only creation, replace_statements content that is the replaced
   * statements ({@code replaced}) wrapped in one try, if, for, while or synchronized: what surround
   * writes.
   */
  static void checkSurround(String replaced, String content) {
    String text = "class X { void m() {\n" + content + "\n}}";
    Statement s = only(text);
    if (s == null) {
      return;
    }
    Statement inner =
        s instanceof TryStatement t
            ? t.getBody()
            : s instanceof IfStatement i && i.getElseStatement() == null
                ? i.getThenStatement()
                : s instanceof ForStatement f
                    ? f.getBody()
                    : s instanceof EnhancedForStatement f
                        ? f.getBody()
                        : s instanceof WhileStatement w
                            ? w.getBody()
                            : s instanceof SynchronizedStatement y ? y.getBody() : null;
    if (!(inner instanceof Block b)) {
      return;
    }
    String body =
        text.substring(b.getStartPosition() + 1, b.getStartPosition() + b.getLength() - 1);
    if (!squeezed(body).equals(squeezed(replaced))) {
      return;
    }
    String head = text.substring(s.getStartPosition(), b.getStartPosition()).strip();
    throw new Refusal(
        ON
            + "the same statements wrapped in "
            + head
            + " is what replace_statements with surround writes. Send surround: \""
            + head
            + "\" in place of content (a try gets the catch of the checked exceptions they"
            + " throw).");
  }

  /** {@code text} without its spacing, for comparing code written at other indentations. */
  private static String squeezed(String text) {
    return text.replaceAll("\\s+", "");
  }

  private static boolean overrides(MethodDeclaration m) {
    for (Object o : m.modifiers()) {
      if (o instanceof Annotation a
          && a.getTypeName().getFullyQualifiedName().endsWith("Override")) {
        return true;
      }
    }
    return false;
  }

  /** Whether each statement of the constructor assigns a parameter to the field of its name. */
  static boolean onlyAssignsParameters(MethodDeclaration m) {
    List<?> statements = m.getBody().statements();
    if (statements.isEmpty() || statements.size() != m.parameters().size()) {
      return false;
    }
    java.util.Set<String> params = new java.util.HashSet<>();
    for (Object o : m.parameters()) {
      params.add(((SingleVariableDeclaration) o).getName().getIdentifier());
    }
    for (Object o : statements) {
      if (!(o instanceof ExpressionStatement e)
          || !(e.getExpression() instanceof Assignment a)
          || a.getOperator() != Assignment.Operator.ASSIGN
          || !(a.getRightHandSide() instanceof SimpleName value)
          || !params.contains(value.getIdentifier())) {
        return false;
      }
      Expression left = a.getLeftHandSide();
      String field =
          left instanceof FieldAccess f && f.getExpression() instanceof ThisExpression
              ? f.getName().getIdentifier()
              : null;
      if (!value.getIdentifier().equals(field)) {
        return false;
      }
    }
    return true;
  }

  private static boolean throwsNotImplemented(MethodDeclaration m) {
    List<?> statements = m.getBody().statements();
    return statements.size() == 1
        && statements.get(0) instanceof ThrowStatement t
        && t.getExpression() instanceof ClassInstanceCreation c
        && c.getType().toString().endsWith("UnsupportedOperationException");
  }

  /** Whether the body is only a call of the supertype's method of its name with its parameters. */
  private static boolean onlyCallsSuper(MethodDeclaration m) {
    List<?> statements = m.getBody().statements();
    if (statements.size() != 1) {
      return false;
    }
    Object s = statements.get(0);
    Expression call =
        s instanceof ReturnStatement r
            ? r.getExpression()
            : s instanceof ExpressionStatement e ? e.getExpression() : null;
    if (!(call instanceof SuperMethodInvocation sup)
        || !sup.getName().getIdentifier().equals(m.getName().getIdentifier())
        || sup.arguments().size() != m.parameters().size()) {
      return false;
    }
    for (int i = 0; i < m.parameters().size(); i++) {
      String p = ((SingleVariableDeclaration) m.parameters().get(i)).getName().getIdentifier();
      if (!(sup.arguments().get(i) instanceof SimpleName n) || !n.getIdentifier().equals(p)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Refuses, under skeleton-only creation, what {@link #checkMembers} refuses in each method that
   * {@code after} adds or changes from {@code before}, unless it had that shape already: the
   * members an edit's own text wrote. {@code tool} names the edit in the refusal.
   */
  static void checkChanged(String before, String after, String rel, String tool) {
    Map<String, MethodDeclaration> old = new HashMap<>();
    for (MethodDeclaration m : Nodes.all(Edits.parses(before, rel), MethodDeclaration.class)) {
      old.put(key(m), m);
    }
    for (MethodDeclaration m : Nodes.all(Edits.parses(after, rel), MethodDeclaration.class)) {
      MethodDeclaration was = old.get(key(m));
      if (was != null
          && (MemberMove.textOf(before, was).equals(MemberMove.textOf(after, m)) || refused(was))) {
        continue;
      }
      checkMembers(
          List.of(m),
          " in an add_declaration in place of this "
              + tool
              + (was == null
                  ? "."
                  : "; it replaces the " + (m.isConstructor() ? "constructor." : "method.")));
    }
  }

  /** Whether {@link #checkMembers} refuses {@code m}. */
  private static boolean refused(MethodDeclaration m) {
    try {
      checkMembers(List.of(m), "");
      return false;
    } catch (Refusal r) {
      return true;
    }
  }

  /** {@code m} by its types and signature: Outer.Inner.m(int). */
  private static String key(MethodDeclaration m) {
    StringBuilder key = new StringBuilder(MemberMove.label(m));
    for (ASTNode n = m.getParent(); n != null; n = n.getParent()) {
      if (n instanceof AbstractTypeDeclaration t) {
        key.insert(0, t.getName().getIdentifier() + ".");
      }
    }
    return key.toString();
  }

  /**
   * Refuses, under skeleton-only creation, statement content that is one statement build writes
   * from names: a local declared from a call or a construction, a for-each, or an index loop. The
   * refusal names the build to send in its place.
   */
  static void checkBuild(String content) {
    String text = "class X { void m() {\n" + content + "\n}}";
    Statement s = only(text);
    if (s == null) {
      return;
    }
    Map<String, Object> entry = new LinkedHashMap<>();
    String what;
    if (s instanceof VariableDeclarationStatement v
        && v.fragments().size() == 1
        && v.modifiers().isEmpty()
        && !v.getType().isVar()
        && ((VariableDeclarationFragment) v.fragments().get(0)).getExtraDimensions() == 0) {
      VariableDeclarationFragment f = (VariableDeclarationFragment) v.fragments().get(0);
      Expression init = f.getInitializer();
      List<?> arguments;
      if (init instanceof MethodInvocation call && call.typeArguments().isEmpty()) {
        entry.put(
            "call",
            (call.getExpression() == null ? "" : source(text, call.getExpression()) + ".")
                + call.getName().getIdentifier());
        arguments = call.arguments();
        what = "a local declared from a call, its type typed,";
      } else if (init instanceof ClassInstanceCreation c
          && c.getAnonymousClassDeclaration() == null
          && c.getExpression() == null
          && c.typeArguments().isEmpty()) {
        Type made =
            c.getType() instanceof ParameterizedType p && p.typeArguments().isEmpty()
                ? p.getType()
                : c.getType();
        entry.put("new", source(text, made));
        arguments = c.arguments();
        what = "a local declared from a construction, its type typed,";
      } else {
        return;
      }
      List<String> args = new ArrayList<>();
      for (Object a : arguments) {
        args.add(source(text, (ASTNode) a));
      }
      if (!args.isEmpty()) {
        entry.put("args", args);
      }
      entry.put("into", f.getName().getIdentifier());
      if (entry.containsKey("new") && !source(text, v.getType()).equals(entry.get("new"))) {
        entry.put("type", source(text, v.getType()));
      }
    } else if (s instanceof EnhancedForStatement f
        && !f.getParameter().getType().isVar()
        && f.getParameter().modifiers().isEmpty()
        && f.getBody() instanceof Block b) {
      entry.put("loop", source(text, f.getExpression()));
      entry.put("name", f.getParameter().getName().getIdentifier());
      body(text, b, entry);
      what = "a loop, its element's type typed,";
    } else if (s instanceof ForStatement f
        && counted(text, f) != null
        && f.getBody() instanceof Block b) {
      entry.put("loop", counted(text, f));
      entry.put("form", "index");
      entry.put(
          "name",
          ((VariableDeclarationFragment)
                  ((VariableDeclarationExpression) f.initializers().get(0)).fragments().get(0))
              .getName()
              .getIdentifier());
      body(text, b, entry);
      what = "an index loop";
    } else {
      return;
    }
    throw new Refusal(
        ON
            + what
            + " is what build writes"
            + (what.contains("typed") ? " with the type read from the code" : "")
            + ". Send build: ["
            + new ObjectMapper().writeValueAsString(entry)
            + "] in place of content.");
  }

  /** The source of {@code n} in {@code text}. */
  private static String source(String text, ASTNode n) {
    return text.substring(n.getStartPosition(), Nodes.end(n));
  }

  /** A loop's body as build's body: each statement as typed, when there are any. */
  private static void body(String text, Block b, Map<String, Object> entry) {
    List<String> body = new ArrayList<>();
    for (Object o : b.statements()) {
      ASTNode n = (ASTNode) o;
      body.add(
          Primitives.indent(
              text.substring(Lines.start(text, n.getStartPosition()), Nodes.end(n)), ""));
    }
    if (!body.isEmpty()) {
      entry.put("body", body);
    }
  }

  /**
   * What {@code f} counts over when it is for (int i = 0; i < n; i++), n an int, a.length or
   * list.size(): n, a or list; else null.
   */
  private static String counted(String text, ForStatement f) {
    if (f.initializers().size() != 1
        || !(f.initializers().get(0) instanceof VariableDeclarationExpression d)
        || d.fragments().size() != 1
        || !d.getType().toString().equals("int")
        || f.updaters().size() != 1
        || !(f.getExpression() instanceof InfixExpression less)
        || less.getOperator() != InfixExpression.Operator.LESS
        || less.hasExtendedOperands()) {
      return null;
    }
    VariableDeclarationFragment i = (VariableDeclarationFragment) d.fragments().get(0);
    String name = i.getName().getIdentifier();
    Object update = f.updaters().get(0);
    boolean steps =
        update instanceof PostfixExpression p
                && p.getOperator() == PostfixExpression.Operator.INCREMENT
                && p.getOperand() instanceof SimpleName n
                && n.getIdentifier().equals(name)
            || update instanceof PrefixExpression pre
                && pre.getOperator() == PrefixExpression.Operator.INCREMENT
                && pre.getOperand() instanceof SimpleName operand
                && operand.getIdentifier().equals(name);
    if (!(i.getInitializer() instanceof NumberLiteral zero)
        || !zero.getToken().equals("0")
        || !steps
        || !(less.getLeftOperand() instanceof SimpleName left)
        || !left.getIdentifier().equals(name)) {
      return null;
    }
    Expression bound = less.getRightOperand();
    if (bound instanceof QualifiedName q && q.getName().getIdentifier().equals("length")) {
      return source(text, q.getQualifier());
    }
    if (bound instanceof FieldAccess a && a.getName().getIdentifier().equals("length")) {
      return source(text, a.getExpression());
    }
    if (bound instanceof MethodInvocation m
        && m.getName().getIdentifier().equals("size")
        && m.arguments().isEmpty()
        && m.getExpression() != null) {
      return source(text, m.getExpression());
    }
    return bound instanceof SimpleName ? source(text, bound) : null;
  }

  /** The one statement of {@code text}, content in a method; null for none or several. */
  private static Statement only(String text) {
    CompilationUnit unit = Jdt.parseSyntax(text);
    if (unit.types().isEmpty()
        || !(((TypeDeclaration) unit.types().get(0)).getMethods().length == 1)) {
      return null;
    }
    List<?> statements =
        ((TypeDeclaration) unit.types().get(0)).getMethods()[0].getBody().statements();
    return statements.size() == 1 ? (Statement) statements.get(0) : null;
  }
}
