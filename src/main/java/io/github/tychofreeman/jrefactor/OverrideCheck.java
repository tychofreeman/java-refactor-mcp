package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Whether a method extract_function makes with a visibility other than private would override, or
 * be overridden by, another method, and whether calls could then run it in another's place.
 */
final class OverrideCheck {

  private OverrideCheck() {}

  private static Set<String> keys(List<ITypeBinding> types) {
    Set<String> out = new LinkedHashSet<>();
    for (ITypeBinding t : types) {
      out.add(t.getErasure().getKey());
    }
    return out;
  }

  /** Whether {@code root} calls {@code m} through super anywhere. */
  private static boolean callsSuper(ASTNode root, IMethodBinding m) {
    return Nodes.all(root, SuperMethodInvocation.class).stream()
        .anyMatch(s ->
            s.resolveMethodBinding() != null
                && s.resolveMethodBinding()
                    .getMethodDeclaration()
                    .getKey()
                    .equals(m.getMethodDeclaration().getKey()));
  }

  /**
   * Why the call naming {@code n} could run a method of {@code here} that overrides it, or null
   * when it cannot: a call on this in a method of a supertype that {@code here} overrides, with no
   * call of that method through super.
   */
  private static String takenOver(SimpleName n, ITypeBinding here) {
    if (!(n.getParent() instanceof MethodInvocation call && call.getName() == n)) {
      return "it is not a plain call";
    }
    if (call.getExpression() != null && !(call.getExpression() instanceof ThisExpression)) {
      return "its receiver may be one";
    }
    MethodDeclaration around = Nodes.ancestor(n, MethodDeclaration.class);
    if (around == null
        || !(around.getParent() instanceof AbstractTypeDeclaration type)
        || type.resolveBinding() == null
        || around.resolveBinding() == null) {
      return "it is not in a method of a named class";
    }
    ITypeBinding owner = type.resolveBinding();
    String ownerKey = owner.getErasure().getKey();
    if (!OverrideCheck.keys(Hierarchy.supertypes(here, true)).contains(ownerKey)
        || ownerKey.equals(here.getErasure().getKey())) {
      return OverrideCheck.keys(Hierarchy.supertypes(owner, true))
              .contains(here.getErasure().getKey())
          ? "it is in " + owner.getName() + " itself"
          : null;
    }
    IMethodBinding caller = around.resolveBinding();
    List<String> params = MemberMove.erasedParams(caller);
    for (ITypeBinding t = here;
        t != null && !t.getErasure().getKey().equals(ownerKey);
        t = t.getSuperclass()) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (m.getName().equals(caller.getName()) && MemberMove.erasedParams(m).equals(params)) {
          return OverrideCheck.callsSuper(n.getRoot(), caller)
              ? caller.getName() + " is also called through super"
              : null;
        }
      }
    }
    return "it is in "
        + owner.getName()
        + "."
        + caller.getName()
        + ", which "
        + here.getName()
        + " does not override";
  }

  /**
   * For a method that is not private: refuses another method of that name in a subclass (it would
   * override the new one) and one of a supertype that the new one would not override (an overload
   * could change which one calls pick). One it overrides is kept in {@link Extraction#overridden}
   * when every call that could now run the new one in its place is in a method this class
   * overrides.
   */
  static void checkOverride(Extraction receiver, List<IVariableBinding> params) throws IOException {
    ITypeBinding here =
        receiver.member.getParent() instanceof AbstractTypeDeclaration t
            ? t.resolveBinding()
            : null;
    if (here == null) {
      throw new Refusal(
          "visibility applies to a method of a named class; extract in the anonymous class with"
              + " the default, private.");
    }
    receiver.refuseEnclosing(here, here.getName());
    List<String> mine = new ArrayList<>();
    for (IVariableBinding p : params) {
      mine.add(p.getType().getErasure().getQualifiedName());
    }
    for (ITypeBinding t : Hierarchy.supertypes(here, true)) {
      if (t.getErasure().getKey().equals(here.getErasure().getKey())) {
        for (IMethodBinding m : t.getDeclaredMethods()) {
          if (m.getName().equals(receiver.name)) {
            throw new Refusal(
                here.getName()
                    + " already has a method "
                    + receiver.name
                    + ", and another of that name could change which one calls pick. Choose"
                    + " another name.");
          }
        }
        continue;
      }
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (!m.getName().equals(receiver.name)) {
          continue;
        }
        int mods = m.getModifiers();
        if (receiver.overridden != null
            || !MemberMove.erasedParams(m).equals(mine)
            || Modifier.isPrivate(mods)
            || Modifier.isStatic(mods)
            || Modifier.isFinal(mods)
            || Modifier.isStatic(receiver.member.getModifiers())) {
          throw new Refusal(
              here.getName()
                  + " (through "
                  + t.getErasure().getName()
                  + ") already has a method "
                  + receiver.name
                  + " that the new one would not override, and another of that name could change"
                  + " which one calls pick. Choose another name.");
        }
        receiver.overridden = m;
      }
    }
    Holding holding = Holding.of(receiver.project, receiver.name, receiver.path);
    String hereKey = here.getErasure().getKey();
    String key =
        receiver.overridden == null ? null : receiver.overridden.getMethodDeclaration().getKey();
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      for (AbstractTypeDeclaration d : Nodes.all(e.getValue(), AbstractTypeDeclaration.class)) {
        ITypeBinding b = d.resolveBinding();
        if (b != null
            && !b.getErasure().getKey().equals(hereKey)
            && OverrideCheck.keys(Hierarchy.supertypes(b, true)).contains(hereKey)) {
          for (IMethodBinding m : b.getDeclaredMethods()) {
            if (m.getName().equals(receiver.name)) {
              throw new Refusal(
                  b.getName()
                      + " already declares "
                      + receiver.name
                      + ", which would override the new method and change what calls of it run."
                      + " Choose another name.");
            }
          }
        }
      }
      if (key == null) {
        continue;
      }
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (n.resolveBinding() instanceof IMethodBinding m
            && m.getMethodDeclaration().getKey().equals(key)
            && !(n.getParent() instanceof SuperMethodInvocation)
            && !(n.getParent() instanceof MethodDeclaration)) {
          String why = OverrideCheck.takenOver(n, here);
          if (why != null) {
            throw new Refusal(
                here.getName()
                    + "."
                    + receiver.name
                    + " would override "
                    + receiver.overridden.getDeclaringClass().getErasure().getName()
                    + "."
                    + receiver.name
                    + ", and the call at line "
                    + e.getValue().getLineNumber(n.getStartPosition())
                    + " of "
                    + receiver.project.relative(e.getKey())
                    + " would then run the new one for a "
                    + here.getName()
                    + ": "
                    + why
                    + ". Choose another name, or keep the method private.");
          }
        }
      }
    }
  }
}
