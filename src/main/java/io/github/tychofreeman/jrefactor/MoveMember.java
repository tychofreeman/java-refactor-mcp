package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

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
    Precondition.require(
        Precondition.that(
            () -> to != null && !to.isBlank(),
            () ->
                "move_member needs to, the top-level type of the same file to move the method to."));
    Qualified q = Qualified.of(name, "Type.method, the class and the method to move");
    String typeName = q.type();
    String member = q.member();
    Opened opened = Opened.of(project, rel);
    Path file = opened.file();
    String text = opened.text();
    CompilationUnit unit = opened.unit();
    TypeDeclaration source = opened.topLevel(typeName);
    String toName = to.substring(to.lastIndexOf('.') + 1);
    Path targetFile =
        !to.contains(".") && MemberMove.typeNamed(unit, to) != null
            ? file
            : targetFile(project, to, rel);
    BodyDeclaration declared = MemberMove.member(source, typeName, member, rel, "move_member");
    if (declared instanceof FieldDeclaration fd) {
      return field(project, opened, fd, typeName, member, toName, targetFile);
    }
    MethodDeclaration m = (MethodDeclaration) declared;
    IMethodBinding method = m.resolveBinding();
    Precondition.require(
        Precondition.that(() -> method != null, () -> name + " does not resolve."),
        Precondition.that(
            () -> !m.isConstructor(),
            () -> name + " is a constructor, which move_member does not move."),
        Precondition.that(
            () -> m.getBody() != null,
            () -> name + " has no body (abstract or native), so there is nothing to move."),
        Precondition.that(
            () -> source.typeParameters().isEmpty(),
            () ->
                typeName
                    + " is generic. The receiver parameter is typed by the class's name, so a"
                    + " method of a generic class is not moved yet."),
        Precondition.that(
            () -> !overrides(m),
            () ->
                name
                    + " overrides a method of a supertype. Moving it would change which method"
                    + " the class runs; leave it in place."));
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
      if (p.equals(file) || p.equals(targetFile) || content.contains(base)) {
        texts.put(p, content);
        toParse.add(p);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, toParse);
    TypeDeclaration target = MemberMove.typeNamed(parsed.get(targetFile), toName);
    Precondition.require(
        Precondition.that(
            () -> target != null && target.resolveBinding() != null,
            () -> to + " is not a class of " + project.relative(targetFile) + " that resolves."));
    boolean crosses = !Objects.equals(project.packageOf(file), project.packageOf(targetFile));

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    List<String> nullable = new ArrayList<>();
    // What the moved text means where it is written: the method, and each call's arguments.
    List<ASTNode> fragments = new ArrayList<>(List.of(m));
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
        for (Object a : site.arguments()) {
          fragments.add((ASTNode) a);
        }
        if (site.getExpression() != null) {
          fragments.add(site.getExpression());
        }
        if (!isStatic && site.getExpression() != null && mayBeNull(site.getExpression())) {
          nullable.add(source(fileText, site.getExpression()) + " in " + project.relative(p));
        }
        list.add(
            rewrite(
                site,
                fileText,
                sourceKey,
                typeName,
                toName,
                member,
                isStatic,
                name,
                key,
                Exceptional.preserved(project)));
      }
      calls += sites.size();
      callFiles++;
    }
    if (!nullable.isEmpty()) {
      Exceptional.report(
          project,
          "a null receiver of "
              + typeName
              + "."
              + member
              + " now fails inside "
              + to
              + "."
              + member
              + ", when it uses the receiver and after the other arguments run (or not at all),"
              + " not at the call: "
              + String.join(", ", nullable));
    }

    int ms = m.getStartPosition();
    int me = ms + m.getLength();
    List<TextEdit> inner = movedEdits(text, m, typeName, sourceKey, isStatic, crosses);
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
    edits
        .computeIfAbsent(targetFile, k -> new ArrayList<>())
        .addAll(MemberMove.appendTo(texts.get(targetFile), target, moved));
    edits.entrySet().removeIf(e -> e.getValue().isEmpty());

    boolean widened = false;
    for (Object o : m.modifiers()) {
      if (o instanceof org.eclipse.jdt.core.dom.Modifier mod && mod.isPrivate()) {
        widened = true;
      }
    }
    NameCheck.require(
        project, texts, edits, NameCheck.meanings(fragments), "moving " + name + " to " + to);
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

  /**
   * move_member of a static field: its declaration moves to the end of {@code toName} in {@code
   * targetFile}, widened from private (to public across packages), and every use names the new
   * class: {@code Shop.MAX} and a bare {@code MAX} become {@code Config.MAX}, a static import of it
   * imports it from there. Its initializer must run alone, since it now runs when the new class is
   * initialized.
   */
  private static Outcome field(
      SourceProject project,
      Opened opened,
      FieldDeclaration fd,
      String typeName,
      String member,
      String toName,
      Path targetFile)
      throws Exception {
    Path file = opened.file();
    String text = opened.text();
    Precondition.require(
        Precondition.that(
            () -> Modifier.isStatic(fd.getModifiers()),
            () ->
                member
                    + " is an instance field; move_member moves methods and static fields. Move"
                    + " instance fields into a new class with extract_struct (fields), or to a"
                    + " supertype or subtype with pull_up or push_down."),
        Precondition.that(
            () -> fd.fragments().size() == 1,
            () -> "the declaration of " + member + " declares several fields. Split it first."));
    VariableDeclarationFragment frag = (VariableDeclarationFragment) fd.fragments().get(0);
    Expression init = frag.getInitializer();
    Precondition.require(
        Precondition.that(
            () -> init == null || ExtractStruct.initializedAlone(init),
            () ->
                "the initializer of "
                    + member
                    + " reads a field, a local or a call, and in "
                    + toName
                    + " it would run when that class is initialized, not "
                    + typeName
                    + ". Make it a constant first, or leave it in place."));
    String key = frag.resolveBinding().getVariableDeclaration().getKey();

    Map<Path, String> texts = new LinkedHashMap<>();
    for (Path p : project.javaFiles()) {
      Path n = p.toAbsolutePath().normalize();
      String content = n.equals(file) ? text : Files.readString(n);
      if (n.equals(file) || n.equals(targetFile) || content.contains(member)) {
        texts.put(n, content);
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    TypeDeclaration target = MemberMove.typeNamed(parsed.get(targetFile), toName);
    Precondition.require(
        Precondition.that(
            () -> target != null && target.resolveBinding() != null,
            () ->
                toName + " is not a class of " + project.relative(targetFile) + " that resolves."));
    String targetPkg = project.packageOf(targetFile);
    boolean crosses = !Objects.equals(project.packageOf(file), targetPkg);
    String qualifiedTo =
        targetPkg == null || targetPkg.isEmpty() ? toName : targetPkg + "." + toName;

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    int uses = 0;
    for (Map.Entry<Path, CompilationUnit> e : parsed.entrySet()) {
      List<TextEdit> list = new ArrayList<>();
      boolean imported = false;
      List<SimpleName> bare = new ArrayList<>();
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (n.isDeclaration()
            || !(n.resolveBinding() instanceof IVariableBinding v)
            || !v.getVariableDeclaration().getKey().equals(key)) {
          continue;
        }
        ASTNode p = n.getParent();
        if (p instanceof QualifiedName q && q.getParent() instanceof ImportDeclaration i) {
          list.add(
              new TextEdit(
                  q.getStartPosition(), Nodes.end(q), qualifiedTo + "." + n.getIdentifier()));
          imported = i.isStatic();
        } else if (p instanceof QualifiedName q && q.getName() == n) {
          list.add(
              new TextEdit(
                  q.getQualifier().getStartPosition(), Nodes.end(q.getQualifier()), toName));
        } else if (p instanceof FieldAccess f && f.getName() == n) {
          list.add(
              new TextEdit(
                  f.getExpression().getStartPosition(), Nodes.end(f.getExpression()), toName));
        } else {
          bare.add(n);
        }
        uses++;
      }
      if (!imported) {
        for (SimpleName n : bare) {
          list.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), toName + "."));
        }
      }
      if (!list.isEmpty()) {
        edits.put(e.getKey(), list);
      }
    }

    int fs = fd.getStartPosition();
    List<TextEdit> inner = new ArrayList<>();
    int at = fd.getType().getStartPosition();
    for (Object o : fd.modifiers()) {
      if (o instanceof org.eclipse.jdt.core.dom.Modifier mod) {
        at = Math.min(at, mod.getStartPosition());
        if (mod.isPrivate() || (crosses && mod.isProtected())) {
          inner.add(
              new TextEdit(
                  mod.getStartPosition() - fs,
                  mod.getStartPosition() + mod.getLength() + 1 - fs,
                  ""));
        }
      }
    }
    if (crosses && !Modifier.isPublic(fd.getModifiers())) {
      inner.add(new TextEdit(at - fs, at - fs, "public "));
    }
    inner.sort((a, b) -> b.start() - a.start());
    String moved = TextEdit.apply(text.substring(fs, Nodes.end(fd)), inner, 0);
    edits.computeIfAbsent(file, k -> new ArrayList<>()).add(MemberMove.removal(text, fd));
    edits
        .computeIfAbsent(targetFile, k -> new ArrayList<>())
        .addAll(MemberMove.appendTo(texts.get(targetFile), target, moved));
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "moving " + typeName + "." + member + " to " + toName,
            "Check that what the field's initializer names resolves in " + toName + ".");
    return new Outcome(
        "moved "
            + typeName
            + "."
            + member
            + " to "
            + toName
            + " ("
            + project.relative(targetFile)
            + "); "
            + uses
            + (uses == 1 ? " use" : " uses")
            + " now name "
            + toName,
        declared);
  }

  /**
   * The project file that declares the top-level class {@code to} ({@code Tax}, or {@code
   * com.example.Tax} when several do), or a refusal naming what to pass.
   */
  private static Path targetFile(SourceProject project, String to, String rel) throws IOException {
    String simple = to.substring(to.lastIndexOf('.') + 1);
    String suffix = "/" + (to.contains(".") ? to.replace('.', '/') : simple) + ".java";
    List<Path> found = new ArrayList<>();
    for (Path p : project.javaFiles()) {
      if (("/" + project.relative(p)).endsWith(suffix)) {
        found.add(p.toAbsolutePath().normalize());
      }
    }
    if (found.isEmpty()) {
      throw new Refusal(
          to
              + " is not a top-level type of "
              + rel
              + " or of a file of the project ("
              + simple
              + ".java). move_member moves a method to a class, or makes it static in its own.");
    }
    if (found.size() > 1) {
      List<String> rels = new ArrayList<>();
      for (Path p : found) {
        rels.add(project.relative(p));
      }
      throw new Refusal(
          "several files declare "
              + simple
              + ": "
              + String.join(", ", rels)
              + ". Name one with its package, as to="
              + project.packageOf(found.get(0))
              + "."
              + simple
              + ".");
    }
    return found.get(0);
  }

  /** Whether {@code m} is marked {@code @Override}. */
  private static boolean overrides(MethodDeclaration m) {
    for (Object o : m.modifiers()) {
      if (o instanceof Annotation a && a.getTypeName().getFullyQualifiedName().equals("Override")) {
        return true;
      }
    }
    return false;
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

  /**
   * The edits that make the moved method's text: the receiver parameter, static, widened (to public
   * when it moves to another package, {@code crosses}).
   */
  private static List<TextEdit> movedEdits(
      String text,
      MethodDeclaration m,
      String typeName,
      String sourceKey,
      boolean isStatic,
      boolean crosses) {
    List<TextEdit> out = new ArrayList<>();
    // static goes before the return type; public before the first modifier, or with static when
    // there is none, as one insertion so the two keep that order.
    String staticText = isStatic ? "" : "static ";
    if (crosses && !Modifier.isPublic(m.getModifiers())) {
      int at =
          m.typeParameters().isEmpty()
              ? m.getReturnType2().getStartPosition()
              : text.lastIndexOf('<', ((ASTNode) m.typeParameters().get(0)).getStartPosition());
      for (Object o : m.modifiers()) {
        if (o instanceof org.eclipse.jdt.core.dom.Modifier mod) {
          at = Math.min(at, mod.getStartPosition());
          if (mod.isProtected()) {
            out.add(
                new TextEdit(
                    mod.getStartPosition(), mod.getStartPosition() + mod.getLength() + 1, ""));
          }
        }
      }
      if (at == m.getReturnType2().getStartPosition()) {
        staticText = "public " + staticText;
      } else {
        out.add(new TextEdit(at, at, "public "));
      }
    }
    if (!staticText.isEmpty()) {
      int returnType = m.getReturnType2().getStartPosition();
      out.add(new TextEdit(returnType, returnType, staticText));
    }
    if (!isStatic) {
      int open = text.indexOf('(', m.getName().getStartPosition() + m.getName().getLength());
      out.add(
          new TextEdit(
              open + 1, open + 1, typeName + " receiver" + (m.parameters().isEmpty() ? "" : ", ")));
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
      String key,
      boolean keep) {
    Expression expr = site.getExpression();
    List<String> parts = new ArrayList<>();
    if (!isStatic) {
      if (expr != null) {
        // A null receiver failed at the call, before the arguments ran; requireNonNull keeps that.
        parts.add(
            keep && mayBeNull(expr)
                ? "java.util.Objects.requireNonNull(" + source(fileText, expr) + ")"
                : source(fileText, expr));
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

  /** Whether {@code receiver} may be null: anything but this, a new object or a literal. */
  private static boolean mayBeNull(Expression receiver) {
    return !(receiver instanceof ThisExpression
        || receiver instanceof ClassInstanceCreation
        || receiver instanceof StringLiteral
        || receiver instanceof TypeLiteral);
  }

  private static boolean callsMember(Expression arg, String key) {
    for (MethodInvocation node : Nodes.all(arg, MethodInvocation.class)) {
      IMethodBinding b = node.resolveMethodBinding();
      if (b != null && b.getKey().equals(key)) {
        return true;
      }
    }
    return false;
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
