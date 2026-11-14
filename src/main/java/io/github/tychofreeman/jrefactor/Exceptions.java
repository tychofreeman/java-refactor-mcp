package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;

/** Which checked exceptions code throws and does not catch. */
final class Exceptions {
  private Exceptions() {}

  /** Whether {@code t} is a checked exception: no {@code RuntimeException} or {@code Error}. */
  static boolean checked(ITypeBinding t) {
    for (ITypeBinding s = t; s != null; s = s.getSuperclass()) {
      String q = s.getQualifiedName();
      if (q.equals("java.lang.RuntimeException") || q.equals("java.lang.Error")) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether a try within {@code top}, around {@code n} (its body or a resource), catches {@code t}.
   */
  static boolean caught(ASTNode n, ASTNode top, ITypeBinding t) {
    ASTNode child = n;
    for (ASTNode p = n.getParent(); p != null && child != top; p = p.getParent()) {
      if (p instanceof TryStatement ts
          && (ts.getBody() == child || ts.resources().contains(child))
          && caughtBy(ts, t)) {
        return true;
      }
      child = p;
    }
    return false;
  }

  /** Whether a catch clause of {@code ts} catches {@code t}. */
  private static boolean caughtBy(TryStatement ts, ITypeBinding t) {
    for (Object o : ts.catchClauses()) {
      Type caught = ((CatchClause) o).getException().getType();
      List<?> alternatives = caught instanceof UnionType u ? u.types() : List.of(caught);
      for (Object a : alternatives) {
        ITypeBinding b = ((Type) a).resolveBinding();
        if (b != null && t.isSubTypeCompatible(b)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The checked exceptions {@code roots} throw and do not catch among themselves, none a subtype of
   * another: those of the calls, constructions and throws that run with them (not inside a lambda
   * or a class they declare), and of the {@code close()} of each resource of a try.
   */
  static List<ITypeBinding> uncaught(List<? extends ASTNode> roots) {
    List<ITypeBinding> types = new ArrayList<>();
    for (ASTNode root : roots) {
      for (ASTNode n : running(root)) {
        for (ITypeBinding t : thrownAt(n)) {
          boolean handled = n instanceof TryStatement ts && caughtBy(ts, t) || caught(n, root, t);
          if (checked(t) && !handled && types.stream().noneMatch(t::isSubTypeCompatible)) {
            types.removeIf(other -> other.isSubTypeCompatible(t));
            types.add(t);
          }
        }
      }
    }
    return types;
  }

  /** The nodes of {@code root} that run with it: not the bodies of lambdas and of classes. */
  private static List<ASTNode> running(ASTNode root) {
    List<ASTNode> out = new ArrayList<>();
    root.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode node) {
            out.add(node);
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
    return out;
  }

  /**
   * What {@code n} itself may throw: a call's or construction's declared exceptions, a throw's
   * value, or for a try with resources each resource's {@code close()}'s.
   */
  private static List<ITypeBinding> thrownAt(ASTNode n) {
    List<ITypeBinding> out = new ArrayList<>();
    IMethodBinding m =
        n instanceof MethodInvocation i
            ? i.resolveMethodBinding()
            : n instanceof SuperMethodInvocation i
                ? i.resolveMethodBinding()
                : n instanceof ClassInstanceCreation c
                    ? c.resolveConstructorBinding()
                    : n instanceof ConstructorInvocation c
                        ? c.resolveConstructorBinding()
                        : n instanceof SuperConstructorInvocation c
                            ? c.resolveConstructorBinding()
                            : null;
    if (m != null) {
      out.addAll(List.of(m.getExceptionTypes()));
    }
    if (n instanceof ThrowStatement t && t.getExpression().resolveTypeBinding() != null) {
      out.add(t.getExpression().resolveTypeBinding());
    }
    if (n instanceof TryStatement ts) {
      for (Object r : ts.resources()) {
        ITypeBinding type = ((Expression) r).resolveTypeBinding();
        if (r instanceof VariableDeclarationExpression v) {
          type = v.getType().resolveBinding();
        }
        IMethodBinding close = type == null ? null : closeOf(type);
        if (close != null) {
          out.addAll(List.of(close.getExceptionTypes()));
        }
      }
    }
    return out;
  }

  /**
   * The {@code close()} that a resource of type {@code t} runs: its own or the nearest inherited.
   */
  private static IMethodBinding closeOf(ITypeBinding t) {
    for (ITypeBinding s : Hierarchy.supertypes(t, true)) {
      for (IMethodBinding m : s.getDeclaredMethods()) {
        if (m.getName().equals("close") && m.getParameterTypes().length == 0) {
          return m;
        }
      }
    }
    return null;
  }
}
