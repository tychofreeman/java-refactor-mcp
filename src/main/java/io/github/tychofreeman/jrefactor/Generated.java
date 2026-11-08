package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

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
    CompilationUnit unit = Jdt.parseSyntax("class X { void m() {\n" + content + "\n}}");
    if (unit.types().isEmpty()
        || !(((TypeDeclaration) unit.types().get(0)).getMethods().length == 1)) {
      return;
    }
    List<?> statements =
        ((TypeDeclaration) unit.types().get(0)).getMethods()[0].getBody().statements();
    if (statements.size() != 1) {
      return;
    }
    Statement s = (Statement) statements.get(0);
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
    String text = "class X { void m() {\n" + content + "\n}}";
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
}
