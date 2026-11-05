package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * The checks before replace_declaration deletes a declaration: nothing else may use it (the uses
 * are named), and a method may not override a concrete one (deleted, its callers would run the
 * inherited one, which compiles).
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
          String base = inherited.getDeclaringClass().getErasure().getName();
          throw new Refusal(
              label
                  + " overrides "
                  + base
                  + "."
                  + inherited.getName()
                  + "(): deleted, its callers would run "
                  + base
                  + "'s instead, which compiles but changes what they do. Keep it, or change"
                  + " it to call super first and then inline it.");
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
}
