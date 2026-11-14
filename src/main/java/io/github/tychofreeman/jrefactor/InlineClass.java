package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

final class InlineClass {
  private InlineClass() {}

  /**
   * inline with field: the class the field {@code field} of {@code holderName} holds, used by
   * nothing else, folds into the holder in the field's place; {@code home.x} becomes {@code x}
   * ({@code this.x} where a local hides it), and the class's file is deleted.
   */
  static Outcome run(SourceProject project, String rel, String holderName, String field)
      throws Exception {
    Opened opened = Opened.of(project, rel);
    TypeDeclaration holder = opened.topLevel(holderName);
    FieldDeclaration fd = null;
    for (FieldDeclaration f : holder.getFields()) {
      for (Object o : f.fragments()) {
        if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(field)) {
          fd = f;
        }
      }
    }
    FieldDeclaration decl = fd;
    Precondition.require(
        Precondition.that(
            () -> decl != null, () -> field + " is not a field of " + holderName + "."),
        Precondition.that(
            () -> decl.fragments().size() == 1,
            () -> "the declaration of " + field + " declares several fields. Split it first."));
    VariableDeclarationFragment frag = (VariableDeclarationFragment) decl.fragments().get(0);
    ITypeBinding type = frag.resolveBinding().getType().getErasure();
    Path classFile = TypeSwitch.fileOf(project, type);
    String cls = type.getName();
    Precondition.require(
        Precondition.that(
            () -> classFile != null && !classFile.equals(opened.file()),
            () ->
                field
                    + " holds "
                    + cls
                    + ", which is not a top-level class of its own file in the project."),
        Precondition.that(
            () ->
                frag.getInitializer() instanceof ClassInstanceCreation c
                    && c.arguments().isEmpty()
                    && c.getAnonymousClassDeclaration() == null,
            () ->
                field
                    + " must start as new "
                    + cls
                    + "(), so its fields' initializers are the state."));
    String key = type.getKey();
    Holding holding = Holding.of(project, cls, opened.file());
    String text = holding.texts().get(opened.file());
    CompilationUnit unit = holding.units().get(opened.file());
    TypeDeclaration h = MemberMove.typeNamed(unit, holderName);
    FieldDeclaration hd = null;
    for (FieldDeclaration f : h.getFields()) {
      if (f.getStartPosition() == decl.getStartPosition()) {
        hd = f;
      }
    }
    FieldDeclaration homeDecl = hd;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      if (e.getKey().equals(classFile)) {
        continue;
      }
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (n.resolveBinding() instanceof ITypeBinding t
            && t.getErasure().getKey().equals(key)
            && !(e.getKey().equals(opened.file()) && Nodes.within(n, homeDecl))) {
          throw new Refusal(
              cls
                  + " is used in "
                  + project.relative(e.getKey())
                  + " line "
                  + e.getValue().getLineNumber(n.getStartPosition())
                  + "; inline folds a class only its holder's field uses.");
        }
      }
    }
    CompilationUnit classUnit = holding.units().get(classFile);
    TypeDeclaration c = MemberMove.typeNamed(classUnit, cls);
    Precondition.require(
        Precondition.that(
            () -> c != null && c.getSuperclassType() == null && c.superInterfaceTypes().isEmpty(),
            () -> cls + " has a superclass or interfaces, which " + holderName + " would not."));
    java.util.Set<String> names = new java.util.HashSet<>();
    for (Object o : h.bodyDeclarations()) {
      if (o instanceof FieldDeclaration f) {
        for (Object x : f.fragments()) {
          names.add(((VariableDeclarationFragment) x).getName().getIdentifier());
        }
      } else if (o instanceof MethodDeclaration m) {
        names.add(m.getName().getIdentifier());
      }
    }
    for (Object o : c.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m && m.isConstructor()) {
        if (!m.parameters().isEmpty() || !m.getBody().statements().isEmpty()) {
          throw new Refusal(
              cls + " has a constructor with work in it, which inline does not fold yet.");
        }
        continue;
      }
      List<String> own = new ArrayList<>();
      if (o instanceof FieldDeclaration f) {
        for (Object x : f.fragments()) {
          own.add(((VariableDeclarationFragment) x).getName().getIdentifier());
        }
      } else if (o instanceof MethodDeclaration m) {
        own.add(m.getName().getIdentifier());
      } else {
        throw new Refusal(
            cls + " has a nested type or initializer, which inline does not fold yet.");
      }
      for (String x : own) {
        if (names.contains(x)) {
          throw new Refusal(
              holderName + " already has " + x + ", as " + cls + " does. Rename one first.");
        }
      }
    }

    refuseOverride(project, opened.file(), h, c);
    String fieldKey = Bindings.declarationKey(frag.resolveBinding());
    List<TextEdit> edits = new ArrayList<>();
    for (SimpleName n : Nodes.all(unit, SimpleName.class)) {
      if (n.isDeclaration()
          || !(n.resolveBinding() instanceof IVariableBinding v)
          || !fieldKey.equals(Bindings.declarationKey(v))) {
        continue;
      }
      ASTNode p = n.getParent();
      if (p instanceof QualifiedName q && q.getQualifier() == n) {
        boolean hidden = hidden(q, q.getName().getIdentifier());
        edits.add(
            new TextEdit(
                n.getStartPosition(), q.getName().getStartPosition(), hidden ? "this." : ""));
      } else if (p instanceof MethodInvocation m && m.getExpression() == n) {
        edits.add(new TextEdit(n.getStartPosition(), m.getName().getStartPosition(), ""));
      } else if (p instanceof FieldAccess inner
          && inner.getName() == n
          && inner.getExpression() instanceof ThisExpression
          && (inner.getParent() instanceof FieldAccess
              || inner.getParent() instanceof MethodInvocation)) {
        edits.add(new TextEdit(inner.getStartPosition(), Nodes.end(inner), "this"));
      } else {
        throw new Refusal(
            field
                + " is used at line "
                + unit.getLineNumber(n.getStartPosition())
                + " other than to reach a member of "
                + cls
                + " (passed, compared or assigned), which would have nothing to name once it is"
                + " folded in.");
      }
    }
    List<?> members = c.bodyDeclarations();
    String classText = holding.texts().get(classFile);
    String body = "";
    if (!members.isEmpty()) {
      int from = Lines.start(classText, ((ASTNode) members.get(0)).getStartPosition());
      int to = Lines.end(classText, Nodes.end((ASTNode) members.get(members.size() - 1)) - 1);
      body = classText.substring(from, to);
      if (!body.endsWith("\n")) {
        body = body + "\n";
      }
    }
    edits.add(
        new TextEdit(
            Lines.start(text, homeDecl.getStartPosition()),
            Lines.end(text, Nodes.end(homeDecl) - 1),
            body));
    String after = TextEdit.apply(text, edits, 0);
    Map<Path, String> changed = new java.util.HashMap<>();
    changed.put(opened.file(), Imports.fix(project, opened.file(), text, after, List.of()).text());
    changed.put(classFile, null);
    Stubs.Declared declared =
        Gate.applyOrRefuse(
            project, changed, "folding " + cls + " into " + holderName, "Nothing was changed.");
    return new Outcome(
        "folded "
            + cls
            + " into "
            + holderName
            + " in place of "
            + field
            + " ("
            + project.relative(classFile)
            + " deleted)",
        declared);
  }

  /** Whether a local or parameter named {@code name} is declared in the method around {@code n}. */
  private static boolean hidden(ASTNode n, String name) {
    MethodDeclaration m = null;
    for (ASTNode p = n; p != null; p = p.getParent()) {
      if (p instanceof MethodDeclaration d) {
        m = d;
        break;
      }
    }
    if (m == null) {
      return false;
    }
    for (VariableDeclaration v : Nodes.all(m, VariableDeclaration.class)) {
      if (v.getName().getIdentifier().equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Refuses a method of {@code c} that, folded into {@code holder}, would override a method of the
   * holder's supertypes or be overridden by one of its subtypes': a call would run another body.
   */
  private static void refuseOverride(
      SourceProject project, Path file, TypeDeclaration holder, TypeDeclaration c)
      throws IOException {
    for (MethodDeclaration m : c.getMethods()) {
      IMethodBinding b = m.resolveBinding();
      if (m.isConstructor() || b == null || Modifier.isStatic(b.getModifiers())) {
        continue;
      }
      List<String> params = new ArrayList<>();
      for (ITypeBinding p : b.getParameterTypes()) {
        params.add(p.getErasure().getQualifiedName());
      }
      Overrides.Clash clash =
          Overrides.of(project, file, holder.resolveBinding(), b.getName(), params);
      if (clash == null) {
        continue;
      }
      String folded = Signatures.label(b);
      String holderName = holder.getName().getIdentifier();
      if (clash.subtype() == null) {
        throw new Refusal(
            "folded into "
                + holderName
                + ", "
                + folded
                + " would override "
                + clash.method()
                + ", so a call of it on a "
                + holderName
                + " would run another body. Rename one of them first.");
      }
      throw new Refusal(
          clash.method()
              + " would then override "
              + folded
              + ", folded into "
              + holderName
              + ", so a call of it on a "
              + clash.subtype().getName()
              + " would run that body instead. Rename one of them first.");
    }
  }
}
