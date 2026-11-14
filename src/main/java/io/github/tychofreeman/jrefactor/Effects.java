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
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

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
    NEW_ARRAY,
    /**
     * What may throw where it stands, as the syntax shows it: a dereference of a variable or an
     * expression ({@code p.x}, {@code e.m()}), an array element, an integer {@code /} or {@code %},
     * a reference cast. Calls may throw too; {@link #mayThrow} counts them.
     */
    MAY_THROW
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
            // A record's own accessor reads a final field, and changes nothing.
            if (!(n.resolveMethodBinding() instanceof IMethodBinding m
                && m.isSyntheticRecordMethod())) {
              add(Kind.CALL, n, null);
            }
            Expression on = n.getExpression();
            if (on != null
                && !(on instanceof ThisExpression)
                && !(on instanceof Name name && name.resolveBinding() instanceof ITypeBinding)) {
              add(Kind.MAY_THROW, n, null);
            }
            return true;
          }

          @Override
          public boolean visit(QualifiedName n) {
            if (n.getQualifier().resolveBinding() instanceof IVariableBinding) {
              add(Kind.MAY_THROW, n, null);
            }
            return true;
          }

          @Override
          public boolean visit(InfixExpression n) {
            ITypeBinding type = n.resolveTypeBinding();
            if ((n.getOperator() == InfixExpression.Operator.DIVIDE
                    || n.getOperator() == InfixExpression.Operator.REMAINDER)
                && type != null
                && type.isPrimitive()
                && !type.getName().equals("double")
                && !type.getName().equals("float")) {
              add(Kind.MAY_THROW, n, null);
            }
            return true;
          }

          @Override
          public boolean visit(CastExpression n) {
            if (!n.getType().isPrimitiveType()) {
              add(Kind.MAY_THROW, n, null);
            }
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
          public boolean visit(ArrayInitializer n) {
            if (!(n.getParent() instanceof ArrayCreation)) {
              add(Kind.NEW_ARRAY, n, null);
            }
            return true;
          }

          @Override
          public boolean visit(ArrayAccess n) {
            add(Kind.ELEMENT, n, null);
            add(Kind.MAY_THROW, n, null);
            return true;
          }

          @Override
          public boolean visit(FieldAccess n) {
            add(Kind.ELEMENT, n, null);
            if (!(n.getExpression() instanceof ThisExpression)) {
              add(Kind.MAY_THROW, n, null);
            }
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
      if (e.kind() != Kind.NAME && e.kind() != Kind.DECLARE && e.kind() != Kind.MAY_THROW) {
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

  /**
   * The nodes that may throw where they stand, outside lambdas and anonymous classes, in order:
   * what {@link Kind#MAY_THROW} marks, and every call and new object.
   */
  List<ASTNode> mayThrow() {
    List<ASTNode> out = new ArrayList<>();
    for (Effect e : all) {
      if (!e.deferred()
          && (e.kind() == Kind.MAY_THROW || e.kind() == Kind.CALL || e.kind() == Kind.CREATE)
          && !out.contains(e.node())) {
        out.add(e.node());
      }
    }
    return out;
  }

  /**
   * Where {@code node} runs later than {@code root}, which holds it, as words ("inside a lambda"),
   * or null when it runs with it.
   */
  static String later(ASTNode node, ASTNode root) {
    for (ASTNode p = node.getParent(); p != null && node != root; node = p, p = p.getParent()) {
      if (p instanceof LambdaExpression) {
        return "inside a lambda";
      }
      if (p instanceof AnonymousClassDeclaration) {
        return "inside an anonymous class";
      }
    }
    return null;
  }

  /**
   * Where {@code node} runs only sometimes, or more than once, when {@code root}, which holds it,
   * runs once, as words ("in a branch of an if"), or null when it runs exactly once.
   */
  static String sometimes(ASTNode node, ASTNode root) {
    for (ASTNode p = node.getParent(); p != null && node != root; node = p, p = p.getParent()) {
      if (p instanceof ConditionalExpression c && node != c.getExpression()) {
        return "in a branch of ?:";
      }
      if (p instanceof InfixExpression in
          && node != in.getLeftOperand()
          && (in.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
              || in.getOperator() == InfixExpression.Operator.CONDITIONAL_OR)) {
        return "to the right of && or ||";
      }
      if (p instanceof IfStatement i && node != i.getExpression()) {
        return "in a branch of an if";
      }
      if ((p instanceof WhileStatement w && node == w.getExpression())
          || (p instanceof DoStatement d && node == d.getExpression())
          || (p instanceof ForStatement f
              && (node == f.getExpression() || f.updaters().contains(node)))) {
        return "in a loop condition, which runs more than once";
      }
      if (p instanceof WhileStatement
          || p instanceof DoStatement
          || (p instanceof ForStatement f && node == f.getBody())
          || (p instanceof EnhancedForStatement ef && node == ef.getBody())) {
        return "in a loop";
      }
      if ((p instanceof SwitchStatement s && node != s.getExpression())
          || (p instanceof SwitchExpression x && node != x.getExpression())) {
        return "in a case of a switch";
      }
      if (p instanceof CatchClause) {
        return "in a catch clause";
      }
    }
    return null;
  }

  /**
   * The outermost loop below {@code root} in which {@code node} runs each pass (its body, condition
   * or update), or null.
   */
  static ASTNode loopOf(ASTNode node, ASTNode root) {
    ASTNode loop = null;
    for (ASTNode p = node.getParent(); p != null && node != root; node = p, p = p.getParent()) {
      if (p instanceof WhileStatement
          || p instanceof DoStatement
          || (p instanceof ForStatement f && !f.initializers().contains(node))
          || (p instanceof EnhancedForStatement ef && node != ef.getExpression())) {
        loop = p;
      }
    }
    return loop;
  }

  /**
   * Whether {@code s} holds a return, or a yield, break or continue that leaves it, outside lambdas
   * and classes declared in it.
   */
  static boolean leaves(Statement s) {
    boolean[] found = {false};
    s.accept(
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
          public boolean visit(TypeDeclarationStatement n) {
            return false;
          }

          @Override
          public boolean visit(ReturnStatement n) {
            found[0] = true;
            return false;
          }

          @Override
          public boolean visit(YieldStatement n) {
            found[0] |= !lands(n, null, false, s);
            return false;
          }

          @Override
          public boolean visit(BreakStatement n) {
            found[0] |= !lands(n, n.getLabel(), true, s);
            return false;
          }

          @Override
          public boolean visit(ContinueStatement n) {
            found[0] |= !lands(n, n.getLabel(), false, s);
            return false;
          }
        });
    return found[0];
  }

  /**
   * Whether the jump {@code n} lands inside {@code s}: on a statement labeled {@code label}, or,
   * unlabeled, on a loop (a switch too for a break; a switch expression for a yield).
   */
  private static boolean lands(Statement n, SimpleName label, boolean isBreak, Statement s) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (n instanceof YieldStatement) {
        if (p instanceof SwitchExpression) {
          return true;
        }
      } else if (label != null) {
        if (p instanceof LabeledStatement l
            && l.getLabel().getIdentifier().equals(label.getIdentifier())) {
          return true;
        }
      } else if (p instanceof ForStatement
          || p instanceof EnhancedForStatement
          || p instanceof WhileStatement
          || p instanceof DoStatement
          || (isBreak && p instanceof SwitchStatement)) {
        return true;
      }
      if (p == s) {
        return false;
      }
    }
    return false;
  }

  /**
   * Whether the code changes state, or may, when it runs: outside lambdas and anonymous class
   * bodies, which run only when called.
   */
  boolean changesNow() {
    return !normalFlowChanges().isEmpty();
  }

  /**
   * The arrays the code creates when it runs, outside lambdas and anonymous class bodies: {@code
   * new int[n]}, and an initializer written alone, {@code {1, 2}}.
   */
  List<ASTNode> newArraysNow() {
    List<ASTNode> out = new ArrayList<>();
    for (Effect e : all) {
      if (e.kind() == Kind.NEW_ARRAY && !e.deferred()) {
        out.add(e.node());
      }
    }
    return out;
  }
}
