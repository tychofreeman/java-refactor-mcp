package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TryStatement;

/**
 * Whether calls can write an instance field: the project code each call runs, followed through the
 * project methods it calls and the project overrides a call could run, writes no field of that
 * declaration, and the library methods it reaches cannot call back into project code. What is not
 * known counts as writing it: an abstract method, a project constructor, a library call given
 * project objects, a functional interface's method, more than {@link #LIMIT} methods.
 */
final class FieldWrites {

  /** The most method bodies followed before the answer is "may write". */
  private static final int LIMIT = 50;

  /**
   * Library methods of a list, deque or iterator that store, take or count elements without calling
   * a method of one.
   */
  private static final Set<String> STORES =
      Set.of(
          "add",
          "addFirst",
          "addLast",
          "offer",
          "offerFirst",
          "offerLast",
          "push",
          "pop",
          "poll",
          "pollFirst",
          "pollLast",
          "peek",
          "peekFirst",
          "peekLast",
          "element",
          "getFirst",
          "getLast",
          "removeFirst",
          "removeLast",
          "get",
          "set",
          "size",
          "isEmpty",
          "clear",
          "hasNext",
          "next",
          "iterator");

  /** The library types {@link #STORES} applies to. */
  private static final List<String> SEQUENCES =
      List.of("java.util.List", "java.util.Deque", "java.util.Iterator");

  private final SourceProject project;
  private final String field;
  private final Map<String, Holding> byName = new HashMap<>();
  private final Set<String> followed = new HashSet<>();

  private FieldWrites(SourceProject project, String field) {
    this.project = project;
    this.field = field;
  }

  /**
   * Whether any of {@code calls} (method calls and constructions) may write {@code field}; a static
   * field always may, since a call can start its class's initialization.
   */
  static boolean mayWrite(SourceProject project, List<ASTNode> calls, IVariableBinding field) {
    if (Modifier.isStatic(field.getModifiers())) {
      return true;
    }
    FieldWrites w = new FieldWrites(project, Bindings.declarationKey(field));
    try {
      for (ASTNode n : calls) {
        if (w.call(n)) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      return true;
    }
  }

  /** Whether the call {@code n} may write the field. */
  private boolean call(ASTNode n) throws IOException {
    IMethodBinding b = Calls.binding(n);
    if (b == null) {
      return true;
    }
    ITypeBinding owner = b.getDeclaringClass();
    if (n instanceof ClassInstanceCreation c) {
      return c.getAnonymousClassDeclaration() != null
          || owner.isFromSource()
          || !quiet(b, null, c.arguments());
    }
    if (!owner.isFromSource()
        && !quiet(
            b,
            n instanceof MethodInvocation m ? m.getExpression() : null,
            n instanceof MethodInvocation m
                ? m.arguments()
                : ((SuperMethodInvocation) n).arguments())) {
      return true;
    }
    boolean virtual =
        n instanceof MethodInvocation
            && !Modifier.isStatic(b.getModifiers())
            && !Modifier.isPrivate(b.getModifiers())
            && !Modifier.isFinal(b.getModifiers());
    String key = Bindings.declarationKey(b);
    boolean own = !owner.isFromSource();
    for (MethodDeclaration d : named(b.getName())) {
      IMethodBinding db = d.resolveBinding();
      if (db == null) {
        continue;
      }
      boolean runs =
          key.equals(Bindings.declarationKey(db))
              || virtual
                  && Hierarchy.overridden(db).stream()
                      .anyMatch(o -> key.equals(Bindings.declarationKey(o)));
      if (!runs) {
        continue;
      }
      own |= key.equals(Bindings.declarationKey(db));
      if (d.getBody() == null || body(d)) {
        return true;
      }
    }
    return !own;
  }

  /** Whether {@code d}'s body may write the field, once per declaration. */
  private boolean body(MethodDeclaration d) throws IOException {
    if (!followed.add(Bindings.declarationKey(d.resolveBinding()))) {
      return false;
    }
    if (followed.size() > LIMIT) {
      return true;
    }
    for (Effects.Effect e : Effects.of(d.getBody()).all()) {
      if (e.kind() == Effects.Kind.WRITE
          && e.variable() != null
          && field.equals(Bindings.declarationKey(e.variable()))) {
        return true;
      }
      if ((e.kind() == Effects.Kind.CALL || e.kind() == Effects.Kind.CREATE) && call(e.node())) {
        return true;
      }
    }
    return callsImplicitly(d);
  }

  /**
   * Whether {@code d}'s body calls code no call names: a string's {@code +} of an object (its
   * toString), a for over an object that is not a list or an array (its iterator), a try's
   * resources (their close).
   */
  private static boolean callsImplicitly(MethodDeclaration d) {
    for (InfixExpression x : Nodes.all(d.getBody(), InfixExpression.class)) {
      if (x.getOperator() == InfixExpression.Operator.PLUS
          && x.resolveTypeBinding() != null
          && "java.lang.String".equals(x.resolveTypeBinding().getQualifiedName())) {
        List<Expression> operands =
            new ArrayList<>(List.of(x.getLeftOperand(), x.getRightOperand()));
        for (Object o : x.extendedOperands()) {
          operands.add((Expression) o);
        }
        if (!operands.stream().allMatch(o -> closed(o.resolveTypeBinding()))) {
          return true;
        }
      }
    }
    for (EnhancedForStatement f : Nodes.all(d.getBody(), EnhancedForStatement.class)) {
      ITypeBinding t = f.getExpression().resolveTypeBinding();
      if (t == null || !t.isArray() && !sequence(t)) {
        return true;
      }
    }
    return Nodes.all(d.getBody(), TryStatement.class).stream()
        .anyMatch(t -> !t.resources().isEmpty());
  }

  /**
   * Whether the library method {@code b}, called on {@code receiver} (null for none, or this) with
   * {@code arguments}, cannot call back into project code: no argument is a lambda or method
   * reference, the receiver is no functional interface, and every argument and the receiver are of
   * {@link #closed} types, but for a method of {@link #STORES} on a list, deque or iterator, which
   * holds elements without calling them.
   */
  private static boolean quiet(IMethodBinding b, Expression receiver, List<?> arguments) {
    ITypeBinding on = receiver == null ? null : receiver.resolveTypeBinding();
    if (on != null && on.getFunctionalInterfaceMethod() != null) {
      return false;
    }
    boolean stores = STORES.contains(b.getName()) && on != null && sequence(on);
    for (Object o : arguments) {
      Expression a = (Expression) o;
      if (a instanceof LambdaExpression
          || a instanceof MethodReference
          || !stores && !closed(a.resolveTypeBinding())) {
        return false;
      }
    }
    return stores || receiver == null || closed(on);
  }

  /** Whether {@code t} is a list, deque or iterator of the library. */
  private static boolean sequence(ITypeBinding t) {
    return !t.getErasure().isFromSource() && SEQUENCES.stream().anyMatch(s -> Hierarchy.isA(t, s));
  }

  /**
   * Whether no object of type {@code t} can be project code: a primitive, or a final library type
   * (an enum or record too) whose type arguments are closed, or an array of one.
   */
  private static boolean closed(ITypeBinding t) {
    if (t == null) {
      return false;
    }
    if (t.isPrimitive() || t.isNullType()) {
      return true;
    }
    if (t.isArray()) {
      return closed(t.getElementType());
    }
    ITypeBinding e = t.getErasure();
    if (e.isFromSource() || !(Modifier.isFinal(e.getModifiers()) || e.isEnum() || e.isRecord())) {
      return false;
    }
    for (ITypeBinding a : t.getTypeArguments()) {
      if (!closed(a)) {
        return false;
      }
    }
    return true;
  }

  /** The method declarations of the project named {@code name}, from one parse per name. */
  private List<MethodDeclaration> named(String name) throws IOException {
    Holding h = byName.get(name);
    if (h == null) {
      h = Holding.of(project, name);
      byName.put(name, h);
    }
    List<MethodDeclaration> out = new ArrayList<>();
    for (CompilationUnit u : h.units().values()) {
      for (MethodDeclaration d : Nodes.all(u, MethodDeclaration.class)) {
        if (d.getName().getIdentifier().equals(name)) {
          out.add(d);
        }
      }
    }
    return out;
  }
}
