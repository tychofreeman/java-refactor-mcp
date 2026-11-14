package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * function_to_method: a static method whose first parameter is of its own class becomes an instance
 * method of that class, the parameter its receiver: {@code static int area(Shape s, int k)} becomes
 * {@code int area(int k)}, {@code s} in the body becomes {@code this}, and {@code Shape.area(x, 3)}
 * becomes {@code x.area(3)}. The inverse is move_member to the method's own class.
 */
final class FunctionToMethod {

  private FunctionToMethod() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    MethodFamily family = MethodFamily.of(project, args);
    MethodDeclaration m = family.target;
    IMethodBinding b = m.resolveBinding();
    Precondition.require(
        Precondition.that(
            () -> b != null && Modifier.isStatic(m.getModifiers()),
            () ->
                family.label
                    + " is not static; function_to_method makes a static method an instance one."),
        Precondition.that(
            () ->
                !m.parameters().isEmpty()
                    && ((SingleVariableDeclaration) m.parameters().get(0)).resolveBinding() != null
                    && sameType(
                        ((SingleVariableDeclaration) m.parameters().get(0))
                            .resolveBinding()
                            .getType(),
                        b.getDeclaringClass()),
            () ->
                "the first parameter of "
                    + family.label
                    + " is not of its own class, so it cannot be the receiver. Move the method to"
                    + " that parameter's class first, or reorder its parameters (move_parameter)."),
        Precondition.that(
            () -> !((SingleVariableDeclaration) m.parameters().get(0)).isVarargs(),
            () -> "the first parameter of " + family.label + " is varargs."),
        Precondition.that(
            () -> family.lambdas.isEmpty() && family.decls.size() == 1,
            () ->
                family.label
                    + " is implemented by lambdas, which function_to_method cannot change."));
    refuseOverriding(project, args.required("file"), b, family.label);
    SingleVariableDeclaration self = (SingleVariableDeclaration) m.parameters().get(0);
    IVariableBinding selfBinding = self.resolveBinding();
    String name = m.getName().getIdentifier();
    Signatures.Decl d = family.decls.get(0);
    SignatureChange change = new SignatureChange(family);
    for (Object o : d.m().modifiers()) {
      if (o instanceof Modifier mod && mod.isStatic()) {
        int end = Nodes.end(mod);
        while (end < d.file().text().length() && d.file().text().charAt(end) == ' ') {
          end++;
        }
        change.replaceRange(d.file(), mod.getStartPosition(), end, "");
      }
    }
    change.remove(d.file(), d.m().parameters(), 0);
    if (d.m().getBody() != null) {
      SingleVariableDeclaration declared = (SingleVariableDeclaration) d.m().parameters().get(0);
      IVariableBinding own = declared.resolveBinding();
      d.m()
          .getBody()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(SimpleName n) {
                  if (own != null && own.isEqualTo(n.resolveBinding())) {
                    ASTNode p = n.getParent();
                    if ((p instanceof Assignment a && a.getLeftHandSide() == n)
                        || p instanceof PostfixExpression
                        || p instanceof PrefixExpression) {
                      throw new Refusal(
                          "the body of "
                              + family.label
                              + " assigns or changes "
                              + n
                              + ", which as the receiver (this) cannot be. Use a local first.");
                    }
                    change.replaceRange(d.file(), n.getStartPosition(), Nodes.end(n), "this");
                  }
                  return true;
                }
              });
    }
    List<String> nullable = new ArrayList<>();
    for (Signatures.Call c : family.calls) {
      if (!(c.node() instanceof MethodInvocation mi) || c.arguments().isEmpty()) {
        throw new Refusal(
            "a call of "
                + family.label
                + " ("
                + c.where(c.node())
                + ") is not one function_to_method rewrites.");
      }
      String text = c.file().text();
      Expression receiver = (Expression) c.arguments().get(0);
      List<String> rest = new ArrayList<>();
      for (int i = 1; i < c.arguments().size(); i++) {
        ASTNode a = (ASTNode) c.arguments().get(i);
        rest.add(text.substring(a.getStartPosition(), Nodes.end(a)));
      }
      String on = text.substring(receiver.getStartPosition(), Nodes.end(receiver));
      if (InlineVariable.needsParentheses(receiver)) {
        on = "(" + on + ")";
      }
      boolean onThis = receiver instanceof ThisExpression t && t.getQualifier() == null;
      if (!onThis && NonNull.mayBeNull(receiver)) {
        nullable.add(on + " in " + c.file().rel());
      }
      change.replaceRange(
          c.file(),
          mi.getStartPosition(),
          Nodes.end(mi),
          (onThis ? "" : on + ".") + name + "(" + String.join(", ", rest) + ")");
    }
    if (!nullable.isEmpty()) {
      Exceptional.change(
          project,
          "a null "
              + selfBinding.getName()
              + " passed to "
              + family.label
              + " now fails at the call, before the other arguments run, not where the body used"
              + " it: "
              + String.join(", ", nullable),
          "keep it static");
    }
    return change.apply(
        project,
        "made "
            + family.label
            + " an instance method of "
            + b.getDeclaringClass().getName()
            + ", "
            + selfBinding.getName()
            + " its receiver",
        args.list("imports"));
  }

  /**
   * Refuses when an instance method of the new signature exists in a subclass of the method's class
   * (it would then override the new one) or in a supertype (the new one would override it): either
   * way a call would run another body than before.
   */
  private static void refuseOverriding(
      SourceProject project, String rel, IMethodBinding b, String label) throws Exception {
    List<String> rest = new ArrayList<>();
    ITypeBinding[] types = b.getParameterTypes();
    for (int i = 1; i < types.length; i++) {
      rest.add(types[i].getErasure().getQualifiedName());
    }
    Overrides.Clash clash =
        Overrides.of(project, project.javaFile(rel), b.getDeclaringClass(), b.getName(), rest);
    if (clash == null) {
      return;
    }
    if (clash.subtype() == null) {
      throw new Refusal(
          "as an instance method, "
              + label
              + " would override "
              + clash.method()
              + ", so a call on its class would run another body. Rename one of them first.");
    }
    throw new Refusal(
        clash.method()
            + " would then override it, so a call of "
            + label
            + " on a "
            + clash.subtype().getName()
            + " would run that body instead. Rename one of them first.");
  }

  private static boolean sameType(ITypeBinding a, ITypeBinding b) {
    return a != null && b != null && a.getErasure().getKey().equals(b.getErasure().getKey());
  }
}
