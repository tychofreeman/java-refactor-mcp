package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * The checks before replace_declaration deletes a declaration: nothing else may use it (the uses
 * are named), and a method may not override a concrete one (deleted, its callers would run the
 * inherited one, which compiles) unless it only calls that one through {@code super} or has the
 * same body, so that its callers run the same code either way.
 */
final class SafeDelete {

  private SafeDelete() {}

  /**
   * Refuses deleting {@code name} of {@code rel} when it is used, or overrides a concrete method.
   */
  static void check(SourceProject project, String rel, String name, String label)
      throws IOException {
    Opened opened = Opened.of(project, rel);
    BodyDeclaration decl = Declarations.find(opened.unit(), name, rel).decl();
    List<String> keys = new ArrayList<>();
    String simple = null;
    switch (decl) {
      case MethodDeclaration m when m.resolveBinding() != null -> {
        IMethodBinding b = m.resolveBinding();
        keys.add(b.getMethodDeclaration().getKey());
        simple = m.isConstructor() ? b.getDeclaringClass().getName() : b.getName();
        IMethodBinding inherited = overridden(b);
        if (inherited != null) {
          if (callsSuper(m, inherited) || sameAsInherited(project, m, inherited, simple)) {
            return;
          }
          String base = inherited.getDeclaringClass().getErasure().getName();
          throw new Refusal(
              label
                  + " overrides "
                  + base
                  + "."
                  + inherited.getName()
                  + "(): deleted, its callers would run "
                  + base
                  + "'s instead, which compiles but changes what they do. Keep it; or, if it"
                  + " should run "
                  + base
                  + "'s, replace its body with "
                  + superCall(m)
                  + " and delete it again.");
        }
      }
      case FieldDeclaration f -> {
        for (Object o : f.fragments()) {
          IVariableBinding v = ((VariableDeclarationFragment) o).resolveBinding();
          if (v != null) {
            keys.add(v.getVariableDeclaration().getKey());
            simple = v.getName();
          }
        }
      }
      case AbstractTypeDeclaration t when t.resolveBinding() != null -> {
        keys.add(t.resolveBinding().getTypeDeclaration().getKey());
        simple = t.getName().getIdentifier();
      }
      default -> {
        return;
      }
    }
    if (simple == null) {
      return;
    }
    int start = decl.getStartPosition();
    int end = start + decl.getLength();
    Map<String, Set<Integer>> uses = new TreeMap<>();
    Holding holding = Holding.of(project, simple, opened.file());
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      Path f = e.getKey();
      CompilationUnit u = e.getValue();
      boolean self = f.equals(opened.file());
      u.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(SimpleName n) {
              if (!n.isDeclaration() && keys.contains(key(n.resolveBinding()))) {
                use(n);
              }
              return true;
            }

            @Override
            public boolean visit(ClassInstanceCreation n) {
              if (n.resolveConstructorBinding() != null
                  && keys.contains(key(n.resolveConstructorBinding()))) {
                use(n);
              }
              return true;
            }

            @Override
            public boolean visit(ConstructorInvocation n) {
              if (n.resolveConstructorBinding() != null
                  && keys.contains(key(n.resolveConstructorBinding()))) {
                use(n);
              }
              return true;
            }

            @Override
            public boolean visit(SuperConstructorInvocation n) {
              if (n.resolveConstructorBinding() != null
                  && keys.contains(key(n.resolveConstructorBinding()))) {
                use(n);
              }
              return true;
            }

            private void use(ASTNode n) {
              if (self && n.getStartPosition() >= start && n.getStartPosition() < end) {
                return;
              }
              uses.computeIfAbsent(project.relative(f).toString(), k -> new TreeSet<>())
                  .add(u.getLineNumber(n.getStartPosition()));
            }
          });
    }
    if (!uses.isEmpty()) {
      List<String> each = new ArrayList<>();
      uses.forEach((file, lines) -> each.add(file + " " + Lines.list(new ArrayList<>(lines))));
      throw new Refusal(
          label
              + " is used in "
              + String.join("; ", each)
              + ". Remove or change those uses first, then delete it.");
    }
  }

  private static String key(IBinding b) {
    return switch (b) {
      case IMethodBinding m -> m.getMethodDeclaration().getKey();
      case IVariableBinding v -> v.getVariableDeclaration().getKey();
      case ITypeBinding t -> t.getTypeDeclaration().getKey();
      case null -> null;
      default -> b.getKey();
    };
  }

  /** The first concrete method of a supertype that {@code method} overrides, or null. */
  private static IMethodBinding overridden(IMethodBinding method) {
    ITypeBinding type = method.getDeclaringClass();
    List<ITypeBinding> todo = new ArrayList<>();
    if (type.getSuperclass() != null) {
      todo.add(type.getSuperclass());
    }
    todo.addAll(List.of(type.getInterfaces()));
    for (int i = 0; i < todo.size(); i++) {
      ITypeBinding t = todo.get(i);
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (!Modifier.isAbstract(m.getModifiers()) && method.overrides(m)) {
          return m;
        }
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return null;
  }

  /** {@code return super.m(p1, ..., pn);}, or without {@code return} for a void method. */
  private static String superCall(MethodDeclaration m) {
    List<String> names = new ArrayList<>();
    for (Object p : m.parameters()) {
      names.add(((SingleVariableDeclaration) p).getName().getIdentifier());
    }
    String call = "super." + m.getName().getIdentifier() + "(" + String.join(", ", names) + ");";
    boolean returns =
        m.getReturnType2() != null
            && !(m.getReturnType2() instanceof PrimitiveType t
                && t.getPrimitiveTypeCode() == PrimitiveType.VOID);
    return returns ? "return " + call : call;
  }

  /**
   * Whether {@code m}'s body only calls {@code inherited} through super, with its parameters in
   * order.
   */
  private static boolean callsSuper(MethodDeclaration m, IMethodBinding inherited) {
    if (m.getBody() == null || m.getBody().statements().size() != 1 || locks(m, inherited)) {
      return false;
    }
    Expression e =
        switch (m.getBody().statements().get(0)) {
          case ReturnStatement r -> r.getExpression();
          case ExpressionStatement s -> s.getExpression();
          default -> null;
        };
    if (!(e instanceof SuperMethodInvocation s)
        || s.getQualifier() != null
        || s.resolveMethodBinding() == null
        || !s.resolveMethodBinding()
            .getMethodDeclaration()
            .getKey()
            .equals(inherited.getMethodDeclaration().getKey())
        || s.arguments().size() != m.parameters().size()) {
      return false;
    }
    for (int i = 0; i < m.parameters().size(); i++) {
      IVariableBinding p = ((SingleVariableDeclaration) m.parameters().get(i)).resolveBinding();
      if (p == null
          || !(s.arguments().get(i) instanceof SimpleName n)
          || !(n.resolveBinding() instanceof IVariableBinding v)
          || !v.getKey().equals(p.getKey())) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code m} is synchronized and {@code inherited} is not: deleted, the lock would go. */
  private static boolean locks(MethodDeclaration m, IMethodBinding inherited) {
    return Modifier.isSynchronized(m.getModifiers())
        && !Modifier.isSynchronized(inherited.getModifiers());
  }

  /**
   * Whether {@code m}'s body means what {@code inherited}'s does, read in one parse of the files
   * that hold {@code simple}: the same code, naming the same declarations (see {@link
   * SameMeaning}).
   */
  private static boolean sameAsInherited(
      SourceProject project, MethodDeclaration m, IMethodBinding inherited, String simple)
      throws IOException {
    if (locks(m, inherited)) {
      return false;
    }
    String own = m.resolveBinding().getMethodDeclaration().getKey();
    String theirs = inherited.getMethodDeclaration().getKey();
    MethodDeclaration sub = null;
    MethodDeclaration base = null;
    for (CompilationUnit u : Holding.of(project, simple, null).units().values()) {
      if (u.findDeclaringNode(own) instanceof MethodDeclaration d) {
        sub = d;
      }
      if (u.findDeclaringNode(theirs) instanceof MethodDeclaration d) {
        base = d;
      }
    }
    return sub != null
        && base != null
        && sub.getBody() != null
        && base.getBody() != null
        && sub.getBody().subtreeMatch(new SameMeaning(sub, base), base.getBody());
  }

  /**
   * Matches the body of {@code sub} against that of {@code base}, which it overrides: names must
   * mean the same. A parameter matches the one at its position, a local or lambda parameter the one
   * declared at its place, a method called on this one it overrides (the call runs the same
   * method), and anything else the same declaration.
   */
  private static final class SameMeaning extends ASTMatcher {
    private final MethodDeclaration sub;
    private final MethodDeclaration base;
    private final Map<String, String> locals = new TreeMap<>();

    SameMeaning(MethodDeclaration sub, MethodDeclaration base) {
      this.sub = sub;
      this.base = base;
    }

    @Override
    public boolean match(SimpleName node, Object other) {
      if (!super.match(node, other)) {
        return false;
      }
      IBinding a = node.resolveBinding();
      IBinding b = ((SimpleName) other).resolveBinding();
      if (a == null || b == null) {
        return false;
      }
      if (a instanceof IVariableBinding va
          && b instanceof IVariableBinding vb
          && !va.isField()
          && !vb.isField()) {
        int i = position(sub, va);
        int j = position(base, vb);
        if (i >= 0 || j >= 0) {
          return i == j;
        }
        String paired = locals.putIfAbsent(va.getKey(), vb.getKey());
        return paired == null || paired.equals(vb.getKey());
      }
      if (a instanceof IMethodBinding ma
          && b instanceof IMethodBinding mb
          && onThis(node)
          && onThis((SimpleName) other)
          && ma.getMethodDeclaration().overrides(mb.getMethodDeclaration())) {
        return true;
      }
      String ka = key(a);
      return ka != null && ka.equals(key(b));
    }

    /** Whether {@code n} names a method called on this: {@code n(...)} or {@code this.n(...)}. */
    private static boolean onThis(SimpleName n) {
      return n.getParent() instanceof MethodInvocation mi
          && mi.getName() == n
          && (mi.getExpression() == null
              || (mi.getExpression() instanceof ThisExpression t && t.getQualifier() == null));
    }

    /** The position of {@code v} among {@code m}'s parameters, or -1. */
    private static int position(MethodDeclaration m, IVariableBinding v) {
      for (int i = 0; i < m.parameters().size(); i++) {
        IVariableBinding p = ((SingleVariableDeclaration) m.parameters().get(i)).resolveBinding();
        if (p != null && p.getKey().equals(v.getKey())) {
          return i;
        }
      }
      return -1;
    }
  }
}
