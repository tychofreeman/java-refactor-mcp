package io.github.tychofreeman.jrefactor;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;

/**
 * move_member: a method moves from its class to another top-level type of the same file. An
 * instance method becomes static in the target and takes the source instance as its first
 * parameter, named receiver; every call in the project passes the instance. A private method is
 * widened to package access, since the target is a different class. Refused: an override, or a
 * method another class overrides; a constructor, a generic class, an anonymous or local class in
 * the body, super, a call of itself, a method reference to it, and a call from another class
 * without an instance.
 */
final class MoveMember {

  private MoveMember() {}

  static Outcome run(SourceProject project, String rel, String name, String to) throws Exception {
    if (to == null || to.isBlank()) {
      throw new Refusal(
          "move_member needs to, the top-level type of the same file to move the method to.");
    }
    int dot = name.indexOf('.');
    if (dot < 0) {
      throw new Refusal("name is Type.method, the class and the method to move, not " + name + ".");
    }
    String typeName = name.substring(0, dot);
    String member = name.substring(dot + 1);
    Path file = project.javaFile(rel).toAbsolutePath().normalize();
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    TypeDeclaration source = MemberMove.typeNamed(unit, typeName);
    if (source == null || source.resolveBinding() == null) {
      throw new Refusal(typeName + " is not a top-level class of " + rel + " that resolves.");
    }
    TypeDeclaration target = MemberMove.typeNamed(unit, to);
    if (target == null || target.resolveBinding() == null || to.equals(typeName)) {
      throw new Refusal(
          to
              + " is not another top-level type of "
              + rel
              + " that resolves. move_member moves a method to a type in the same file.");
    }
    BodyDeclaration declared = MemberMove.member(source, typeName, member, rel, "move_member");
    if (!(declared instanceof MethodDeclaration m)) {
      throw new Refusal(
          name
              + " is a field. move_member moves a method; push_down or pull_up move a field to a"
              + " subtype or supertype.");
    }
    IMethodBinding method = m.resolveBinding();
    if (method == null) {
      throw new Refusal(name + " does not resolve.");
    }
    if (m.isConstructor()) {
      throw new Refusal(name + " is a constructor, which move_member does not move.");
    }
    if (m.getBody() == null) {
      throw new Refusal(name + " has no body (abstract or native), so there is nothing to move.");
    }
    if (!source.typeParameters().isEmpty()) {
      throw new Refusal(
          typeName
              + " is generic. The receiver parameter is typed by the class's name, so a method of"
              + " a generic class is not moved yet.");
    }
    for (Object o : m.modifiers()) {
      if (o instanceof Annotation a && a.getTypeName().getFullyQualifiedName().equals("Override")) {
        throw new Refusal(
            name
                + " overrides a method of a supertype. Moving it would change which method the"
                + " class runs; leave it in place.");
      }
    }
    checkBody(m, method, name);

    boolean isStatic = Modifier.isStatic(m.getModifiers());
    String key = method.getKey();
    String sourceKey = source.resolveBinding().getKey();
    String base = member.contains("(") ? member.substring(0, member.indexOf('(')).strip() : member;

    List<Path> candidates =
        project.javaFiles().stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
    Map<Path, String> texts = new LinkedHashMap<>();
    List<Path> toParse = new ArrayList<>();
    for (Path p : candidates) {
      String content = p.equals(file) ? text : Files.readString(p);
      if (p.equals(file) || content.contains(base)) {
        texts.put(p, content);
        toParse.add(p);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, toParse);

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    int calls = 0;
    int callFiles = 0;
    for (Path p : parsed.keySet()) {
      CompilationUnit cu = parsed.get(p);
      String fileText = texts.get(p);
      List<MethodInvocation> sites = new ArrayList<>();
      List<MethodDeclaration> decls = new ArrayList<>();
      cu.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
              decls.add(node);
              return true;
            }

            @Override
            public boolean visit(MethodInvocation node) {
              IMethodBinding b = node.resolveMethodBinding();
              if (b != null && b.getKey().equals(key)) {
                sites.add(node);
              }
              return true;
            }

            @Override
            public boolean preVisit2(ASTNode node) {
              if (node instanceof MethodReference r
                  && r.resolveMethodBinding() != null
                  && r.resolveMethodBinding().getKey().equals(key)) {
                throw new Refusal(
                    name
                        + " is used as a method reference in "
                        + p.getFileName()
                        + ". move_member does not rewrite method references; replace the reference"
                        + " with a lambda first.");
              }
              return true;
            }
          });
      for (MethodDeclaration d : decls) {
        IMethodBinding b = d.resolveBinding();
        if (b != null && !b.getKey().equals(key) && b.overrides(method)) {
          throw new Refusal(
              name
                  + " is overridden by "
                  + d.getName().getIdentifier()
                  + " in "
                  + p.getFileName()
                  + ". Moving it would change what that class runs; leave it in place.");
        }
      }
      if (sites.isEmpty()) {
        continue;
      }
      List<TextEdit> list = edits.computeIfAbsent(p, k -> new ArrayList<>());
      for (MethodInvocation site : sites) {
        list.add(rewrite(site, fileText, sourceKey, typeName, to, member, isStatic, name, key));
      }
      calls += sites.size();
      callFiles++;
    }

    int ms = m.getStartPosition();
    int me = ms + m.getLength();
    List<TextEdit> inner = movedEdits(text, m, typeName, sourceKey, isStatic);
    List<TextEdit> shifted = new ArrayList<>();
    for (TextEdit e : inner) {
      shifted.add(new TextEdit(e.start() - ms, e.end() - ms, e.text()));
    }
    shifted.sort((a, b) -> b.start() - a.start());
    String moved = TextEdit.apply(text.substring(ms, me), shifted, 0);

    int start = Lines.start(text, ms);
    int end = Lines.end(text, me - 1);
    if (end < text.length() && text.charAt(end) == '\n') {
      end++;
    } else if (text.startsWith("}", end) && text.substring(0, start).endsWith("\n\n")) {
      start--;
    }
    List<TextEdit> own = edits.computeIfAbsent(file, k -> new ArrayList<>());
    own.add(new TextEdit(start, end, ""));
    own.addAll(MemberMove.appendTo(text, target, moved));
    edits.entrySet().removeIf(e -> e.getValue().isEmpty());

    boolean widened = false;
    for (Object o : m.modifiers()) {
      if (o instanceof org.eclipse.jdt.core.dom.Modifier mod && mod.isPrivate()) {
        widened = true;
      }
    }
    Stubs.Declared declared2 =
        MemberMove.gate(
            project,
            texts,
            edits,
            "moving " + name + " to " + to,
            "Check that the moved method's names resolve in " + to + ", where it now lives.");
    return new Outcome(
        "moved "
            + typeName
            + "."
            + member
            + " to "
            + to
            + " in "
            + rel
            + "; "
            + calls
            + " call"
            + (calls == 1 ? "" : "s")
            + " rewritten in "
            + callFiles
            + " file"
            + (callFiles == 1 ? "" : "s")
            + (widened ? "; private widened to package access" : ""),
        declared2);
  }

  /** The refusals about the body: what its names would mean in the target. */
  private static void checkBody(MethodDeclaration m, IMethodBinding method, String name) {
    m.getBody()
        .accept(
            new ASTVisitor() {
              @Override
              public boolean visit(AnonymousClassDeclaration node) {
                throw anonymous(name);
              }

              @Override
              public boolean visit(TypeDeclarationStatement node) {
                throw anonymous(name);
              }

              @Override
              public boolean visit(SuperFieldAccess node) {
                throw new Refusal(name + " uses super. move_member does not move a super use yet.");
              }

              @Override
              public boolean visit(SuperMethodInvocation node) {
                throw new Refusal(name + " uses super. move_member does not move a super use yet.");
              }

              @Override
              public boolean visit(ThisExpression node) {
                if (node.getQualifier() != null) {
                  throw new Refusal(
                      name + " uses a qualified this (Outer.this), which does not move.");
                }
                return true;
              }

              @Override
              public boolean visit(SimpleName node) {
                if (node.getIdentifier().equals("receiver")) {
                  throw new Refusal(
                      name
                          + " uses the name receiver, which move_member gives the receiver"
                          + " parameter. Rename it first.");
                }
                return true;
              }

              @Override
              public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                if (b != null && b.getKey().equals(method.getKey())) {
                  throw new Refusal(
                      name
                          + " calls itself. A recursive call would go through the receiver; move it"
                          + " by hand.");
                }
                return true;
              }
            });
  }

  private static Refusal anonymous(String name) {
    return new Refusal(
        name
            + " contains an anonymous class or a local class, whose this is not the receiver, so"
            + " move_member does not move it yet.");
  }

  /** The edits that make the moved method's text: the receiver parameter, static, widened. */
  private static List<TextEdit> movedEdits(
      String text, MethodDeclaration m, String typeName, String sourceKey, boolean isStatic) {
    List<TextEdit> out = new ArrayList<>();
    if (!isStatic) {
      int open = text.indexOf('(', m.getName().getStartPosition() + m.getName().getLength());
      out.add(
          new TextEdit(
              open + 1, open + 1, typeName + " receiver" + (m.parameters().isEmpty() ? "" : ", ")));
      int returnType = m.getReturnType2().getStartPosition();
      out.add(new TextEdit(returnType, returnType, "static "));
    }
    for (Object o : m.modifiers()) {
      if (o instanceof org.eclipse.jdt.core.dom.Modifier mod && mod.isPrivate()) {
        out.add(
            new TextEdit(mod.getStartPosition(), mod.getStartPosition() + mod.getLength() + 1, ""));
      }
    }
    m.getBody()
        .accept(
            new ASTVisitor() {
              @Override
              public boolean visit(ThisExpression node) {
                if (!isStatic) {
                  int s = node.getStartPosition();
                  out.add(new TextEdit(s, s + node.getLength(), "receiver"));
                }
                return true;
              }

              @Override
              public boolean visit(SimpleName node) {
                if (node.resolveBinding() instanceof IVariableBinding v
                    && v.isField()
                    && v.getDeclaringClass() != null
                    && v.getDeclaringClass().getKey().equals(sourceKey)
                    && !isNamePart(node)) {
                  int s = node.getStartPosition();
                  if (Modifier.isStatic(v.getModifiers())) {
                    out.add(new TextEdit(s, s, typeName + "."));
                  } else if (!isStatic) {
                    out.add(new TextEdit(s, s, "receiver."));
                  }
                }
                return true;
              }

              @Override
              public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                if (node.getExpression() == null
                    && b != null
                    && b.getDeclaringClass() != null
                    && b.getDeclaringClass().getKey().equals(sourceKey)) {
                  int s = node.getStartPosition();
                  if (Modifier.isStatic(b.getModifiers())) {
                    out.add(new TextEdit(s, s, typeName + "."));
                  } else if (!isStatic) {
                    out.add(new TextEdit(s, s, "receiver."));
                  }
                }
                return true;
              }
            });
    return out;
  }

  private static boolean isNamePart(SimpleName node) {
    ASTNode parent = node.getParent();
    if (parent instanceof FieldAccess fa) {
      return fa.getName() == node;
    }
    if (parent instanceof QualifiedName qn) {
      return qn.getName() == node;
    }
    return parent instanceof org.eclipse.jdt.core.dom.VariableDeclarationFragment vdf
        && vdf.getName() == node;
  }

  /** The call rewritten to pass the instance (or nothing, for a static method). */
  private static TextEdit rewrite(
      MethodInvocation site,
      String fileText,
      String sourceKey,
      String typeName,
      String to,
      String member,
      boolean isStatic,
      String name,
      String key) {
    Expression expr = site.getExpression();
    List<String> parts = new ArrayList<>();
    if (!isStatic) {
      if (expr != null) {
        parts.add(source(fileText, expr));
      } else {
        ASTNode owner = owningType(site);
        if (owner instanceof AnonymousClassDeclaration) {
          throw new Refusal(
              name
                  + " is called from an anonymous class, where this is not the instance. Call it"
                  + " from a method of "
                  + typeName
                  + " first.");
        }
        boolean inSource =
            owner instanceof TypeDeclaration td
                && td.resolveBinding() != null
                && td.resolveBinding().getKey().equals(sourceKey);
        if (!inSource) {
          throw new Refusal(
              name
                  + " is called without a receiver from another class; qualify the call with an"
                  + " instance of "
                  + typeName
                  + ".");
        }
        parts.add("this");
      }
    } else if (expr != null && !(expr instanceof Name)) {
      throw new Refusal(
          name
              + " is static and called through an expression. Call it as "
              + typeName
              + "."
              + member
              + " first.");
    }
    for (Object o : site.arguments()) {
      Expression arg = (Expression) o;
      if (callsMember(arg, key)) {
        throw new Refusal(
            name
                + " is called inside another argument of a call. Move the inner call into a"
                + " variable first.");
      }
      parts.add(source(fileText, arg));
    }
    int s = site.getStartPosition();
    return new TextEdit(
        s, s + site.getLength(), to + "." + member + "(" + String.join(", ", parts) + ")");
  }

  private static boolean callsMember(Expression arg, String key) {
    boolean[] found = {false};
    arg.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodInvocation node) {
            IMethodBinding b = node.resolveMethodBinding();
            if (b != null && b.getKey().equals(key)) {
              found[0] = true;
            }
            return true;
          }
        });
    return found[0];
  }

  private static ASTNode owningType(ASTNode node) {
    for (ASTNode p = node.getParent(); p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration || p instanceof AnonymousClassDeclaration) {
        return p;
      }
    }
    return null;
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
  }
}
