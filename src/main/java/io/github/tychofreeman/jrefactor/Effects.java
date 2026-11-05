package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * What code does when it runs, in its normal flow: each variable it names, declares or writes, each
 * array element or field it reaches through an expression, and each call and object it makes, in
 * source order, marked when it runs later (in a lambda or an anonymous class). The one model of
 * effects for the tools that move code; what may throw is room for a further kind.
 */
final class Effects {

  enum Kind {
    /** A variable named: read, or the name of one written or declared. */
    NAME,
    /** An array element or a field reached through an expression: {@code a[i]}, {@code this.x}. */
    ELEMENT,
    DECLARE,
    WRITE,
    CALL,
    CREATE,
    NEW_ARRAY
  }

  /**
   * One thing the code does: what kind, where, the variable named, declared or written (null for
   * the others, and for a write to an array element), and whether it runs later than the code
   * around it.
   */
  record Effect(Kind kind, ASTNode node, IVariableBinding variable, boolean deferred) {

    /** Whether it changes state, or may: a write, a call, a new object. */
    boolean changes() {
      return kind == Kind.WRITE || kind == Kind.CALL || kind == Kind.CREATE;
    }

    int end() {
      return node.getStartPosition() + node.getLength();
    }
  }

  private final List<Effect> all;

  private Effects(List<Effect> all) {
    this.all = all;
  }

  /** The effects of {@code node}, in source order. */
  static Effects of(ASTNode node) {
    List<Effect> all = new ArrayList<>();
    int[] deferred = {0};
    node.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(LambdaExpression n) {
            deferred[0]++;
            return true;
          }

          @Override
          public void endVisit(LambdaExpression n) {
            deferred[0]--;
          }

          @Override
          public boolean visit(AnonymousClassDeclaration n) {
            deferred[0]++;
            return true;
          }

          @Override
          public void endVisit(AnonymousClassDeclaration n) {
            deferred[0]--;
          }

          @Override
          public boolean visit(SimpleName n) {
            if (n.resolveBinding() instanceof IVariableBinding v) {
              add(Kind.NAME, n, v);
            }
            return true;
          }

          @Override
          public boolean visit(VariableDeclarationFragment n) {
            add(Kind.DECLARE, n, n.resolveBinding());
            return true;
          }

          @Override
          public boolean visit(Assignment n) {
            add(Kind.WRITE, n, target(n.getLeftHandSide()));
            return true;
          }

          @Override
          public boolean visit(PrefixExpression n) {
            if (n.getOperator() == PrefixExpression.Operator.INCREMENT
                || n.getOperator() == PrefixExpression.Operator.DECREMENT) {
              add(Kind.WRITE, n, target(n.getOperand()));
            }
            return true;
          }

          @Override
          public boolean visit(PostfixExpression n) {
            add(Kind.WRITE, n, target(n.getOperand()));
            return true;
          }

          @Override
          public boolean visit(MethodInvocation n) {
            add(Kind.CALL, n, null);
            return true;
          }

          @Override
          public boolean visit(SuperMethodInvocation n) {
            add(Kind.CALL, n, null);
            return true;
          }

          @Override
          public boolean visit(ClassInstanceCreation n) {
            add(Kind.CREATE, n, null);
            return true;
          }

          @Override
          public boolean visit(ArrayCreation n) {
            add(Kind.NEW_ARRAY, n, null);
            return true;
          }

          @Override
          public boolean visit(ArrayAccess n) {
            add(Kind.ELEMENT, n, null);
            return true;
          }

          @Override
          public boolean visit(FieldAccess n) {
            add(Kind.ELEMENT, n, null);
            return true;
          }

          @Override
          public boolean visit(SuperFieldAccess n) {
            add(Kind.ELEMENT, n, null);
            return true;
          }

          private void add(Kind kind, ASTNode n, IVariableBinding v) {
            all.add(new Effect(kind, n, v, deferred[0] > 0));
          }
        });
    return new Effects(all);
  }

  /** The variable an assignment or increment writes: a name or a field access, else null. */
  private static IVariableBinding target(Expression e) {
    Name name = e instanceof FieldAccess f ? f.getName() : e instanceof Name n ? n : null;
    return name != null && name.resolveBinding() instanceof IVariableBinding v ? v : null;
  }

  List<Effect> all() {
    return all;
  }

  /** Whether the code changes state or may, anywhere, a lambda's body included. */
  boolean changes() {
    return all.stream().anyMatch(Effect::changes);
  }

  /** The variables named, in order, each as declared (one per use). */
  List<IVariableBinding> named() {
    List<IVariableBinding> out = new ArrayList<>();
    for (Effect e : all) {
      if (e.kind() == Kind.NAME) {
        out.add(e.variable().getVariableDeclaration());
      }
    }
    return out;
  }

  /** The variables declared or written, each as declared. */
  Set<IVariableBinding> written() {
    Set<IVariableBinding> out = new LinkedHashSet<>();
    for (Effect e : all) {
      if ((e.kind() == Kind.DECLARE || e.kind() == Kind.WRITE) && e.variable() != null) {
        out.add(e.variable().getVariableDeclaration());
      }
    }
    return out;
  }

  /** Whether {@code variable} is assigned, incremented or decremented at or after {@code from}. */
  boolean writes(IVariableBinding variable, int from) {
    for (Effect e : all) {
      if (e.kind() == Kind.WRITE
          && e.node().getStartPosition() >= from
          && e.variable() != null
          && e.variable().getVariableDeclaration().isEqualTo(variable.getVariableDeclaration())) {
        return true;
      }
    }
    return false;
  }

  /** The nodes that change state or may, outside lambdas and anonymous classes, in order. */
  List<ASTNode> normalFlowChanges() {
    List<ASTNode> out = new ArrayList<>();
    for (Effect e : all) {
      if (e.changes() && !e.deferred()) {
        out.add(e.node());
      }
    }
    return out;
  }

  /**
   * Whether the code, outside lambdas, is only literals, locals, parameters, constants and
   * operators on them: nothing that code run before or after it could change, nor that runs twice.
   */
  boolean fixed() {
    for (Effect e : all) {
      if (e.deferred()) {
        continue;
      }
      if (e.kind() != Kind.NAME && e.kind() != Kind.DECLARE) {
        return false;
      }
      if (e.kind() == Kind.NAME
          && e.variable().isField()
          && e.variable().getConstantValue() == null) {
        return false;
      }
    }
    return true;
  }
}
