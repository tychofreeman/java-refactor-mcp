package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Moves a static nested type to its own file, as a top-level type of the same package: every
 * reference in the project follows it, its uses of the outer type's static members are qualified,
 * and private members used across the new boundary become package-private.
 */
final class MoveType {

  private MoveType() {}

  /** Moves the nested type {@code name} ("Outer.Inner") declared in {@code rel}. */
  static Outcome run(SourceProject project, String rel, String name) throws IOException {
    Path file = project.javaFile(rel);
    String simple = name.substring(name.lastIndexOf('.') + 1);
    Holding holding = Holding.of(project, simple, file);
    Map<Path, String> held = holding.texts();
    Map<Path, CompilationUnit> parsed = holding.units();
    String source = held.get(file);
    CompilationUnit unit = parsed.get(file);
    AbstractTypeDeclaration decl = find(unit, name, rel);
    ITypeBinding type = decl.resolveBinding();
    if (type == null) {
      throw new Refusal(name + " does not resolve. Fix the compile errors in " + rel + " first.");
    }
    String outerLabel = name.substring(0, name.lastIndexOf('.'));
    if (decl instanceof TypeDeclaration t
        && !t.isInterface()
        && !Modifier.isStatic(t.getModifiers())) {
      throw new Refusal(
          name
              + " is an inner class (not static), so it may use an instance of "
              + outerLabel
              + ". Make it static first, passing what it uses from "
              + outerLabel
              + ", then move it.");
    }
    Path target = file.resolveSibling(simple + ".java");
    if (Files.exists(target)) {
      throw new Refusal(
          project.relative(target) + " already exists. Rename " + name + " first, then move it.");
    }
    String pkg = packageOf(unit);
    String key = type.getTypeDeclaration().getKey();
    String outerKey = type.getDeclaringClass().getTypeDeclaration().getKey();
    int start = decl.getStartPosition();
    int end = start + decl.getLength();

    Map<Path, String> changed = new LinkedHashMap<>();
    Map<String, List<Integer>> lines = new TreeMap<>();
    int references = 0;
    TreeMap<Integer, String> widened = new TreeMap<>();
    Set<String> accessed = new TreeSet<>();
    List<String> sealed = new ArrayList<>();
    for (Map.Entry<Path, String> e : held.entrySet()) {
      Path f = e.getKey();
      String text = e.getValue();
      CompilationUnit u = parsed.get(f);
      boolean samePackage = packageOf(u).equals(pkg);
      List<TextEdit> edits = new ArrayList<>();
      List<ASTNode> refs = new ArrayList<>();
      List<SimpleName> named = new ArrayList<>();
      references(u, text, key, pkg, simple, samePackage, edits, refs, named);
      // A file of another package that names the type without importing it reaches it through a
      // type it inherits; once the type is top-level, it needs the import.
      boolean inherits =
          !samePackage
              && !f.equals(file)
              && !named.isEmpty()
              && refs.stream().noneMatch(r -> r instanceof ImportDeclaration);
      if (inherits) {
        refs.addAll(named);
      }
      if (!refs.isEmpty()) {
        Set<Integer> at = new TreeSet<>();
        for (ASTNode r : refs) {
          at.add(u.getLineNumber(r.getStartPosition()));
        }
        lines.put(project.relative(f).toString(), new ArrayList<>(at));
        references += refs.size();
      }
      if (!f.equals(file)) {
        if (!edits.isEmpty() || inherits) {
          String after = apply(text, 0, edits);
          if (inherits) {
            after = Imports.adding(after, Jdt.parseSyntax(after), qualified(pkg, simple));
          }
          // Another package's file imports the moved type, and drops an import it no longer uses.
          if (!samePackage) {
            after = Imports.fix(project, f, text, after, List.of(qualified(pkg, simple))).text();
          }
          changed.put(f, after);
        }
        continue;
      }
      Set<String> qualifiers = new HashSet<>();
      qualifyAndWiden(
          u,
          text,
          decl,
          type,
          key,
          outerKey,
          outerLabel,
          simple,
          edits,
          widened,
          accessed,
          qualifiers);
      permits(
          u,
          text,
          start,
          end,
          pkg,
          name,
          simple,
          key,
          outerKey,
          outerLabel,
          edits,
          widened,
          sealed);
      for (Object o : decl.modifiers()) {
        if (o instanceof Modifier m && (m.isStatic() || m.isPrivate() || m.isProtected())) {
          edits.add(dropModifier(text, m));
        }
      }
      // A member type of an interface is public without saying so; top-level, it must say so.
      if (type.getDeclaringClass().isInterface() && !Modifier.isPublic(decl.getModifiers())) {
        int at = afterJavadoc(text, decl);
        if (!decl.modifiers().isEmpty()) {
          at = ((ASTNode) decl.modifiers().get(0)).getStartPosition();
        }
        edits.add(new TextEdit(at, at, "public "));
      }
      List<TextEdit> inside = new ArrayList<>();
      List<TextEdit> outside = new ArrayList<>();
      for (TextEdit edit : edits) {
        (edit.start() >= start && edit.start() < end ? inside : outside).add(edit);
      }
      int lineStart = Lines.start(text, start);
      String moved = dedent(apply(text.substring(lineStart, end), lineStart, inside), text, start);
      Set<String> usedInside = new HashSet<>();
      Set<String> usedOutside = new HashSet<>();
      identifiers(u, start, end, usedInside, usedOutside);
      usedInside.addAll(qualifiers);
      StringBuilder kept = new StringBuilder();
      Boolean lastStatic = null;
      for (Object o : u.imports()) {
        ImportDeclaration i = (ImportDeclaration) o;
        String last = simpleName(i.getName().getFullyQualifiedName());
        boolean used = i.isOnDemand() || usedInside.contains(last);
        if (!used) {
          continue;
        }
        if (lastStatic != null && lastStatic != i.isStatic()) {
          kept.append('\n');
        }
        lastStatic = i.isStatic();
        kept.append(
            text, Lines.start(text, i.getStartPosition()), Lines.end(text, i.getStartPosition()));
        if (!i.isOnDemand() && !usedOutside.contains(last)) {
          outside.add(
              new TextEdit(
                  Lines.start(text, i.getStartPosition()),
                  Lines.end(text, i.getStartPosition()),
                  ""));
        }
      }
      int cutStart = lineStart;
      int before = Lines.start(text, Math.max(0, cutStart - 1));
      if (cutStart > 0 && text.substring(before, cutStart).isBlank()) {
        cutStart = before;
      }
      outside.add(new TextEdit(cutStart, Lines.end(text, end), ""));
      changed.put(f, apply(text, 0, outside));
      String created =
          (pkg.isEmpty() ? "" : "package " + pkg + ";\n\n")
              + (kept.isEmpty() ? "" : kept + "\n")
              + moved
              + "\n";
      changed.put(target, created);
    }

    NameCheck.require(
        project,
        Map.of(target, ""),
        Map.of(target, List.of(new TextEdit(0, 0, changed.get(target)))),
        NameCheck.meanings(List.of(decl)),
        "moving " + name);
    Stubs.Declared declared =
        Gate.applyOrRefuse(
            project,
            changed,
            "moving " + name + " to " + project.relative(target),
            "Fix what the errors name, then move it again; when the moved type's name clashes"
                + " with another type's, rename one of them first.");
    String result =
        "moved "
            + name
            + " to "
            + project.relative(target)
            + " ("
            + changed.get(target).lines().count()
            + " lines)";
    if (references > 0) {
      List<String> each = new ArrayList<>();
      lines.forEach((r, ls) -> each.add(r + " " + Lines.list(ls)));
      result +=
          ", with "
              + plural(references, "reference")
              + " in "
              + plural(lines.size(), "file")
              + ": "
              + String.join("; ", each);
    }
    if (!widened.isEmpty()) {
      result += "; widened " + joinAnd(new ArrayList<>(widened.values())) + " to package-private";
    }
    if (!accessed.isEmpty()) {
      result += "; reads of " + joinAnd(new ArrayList<>(accessed)) + " became accessor calls";
    }
    if (!sealed.isEmpty()) {
      result +=
          "; wrote a permits clause on "
              + joinAnd(sealed)
              + ", as the move put permitted subtypes in another file";
    }
    return new Outcome(result, declared);
  }

  /**
   * The nested type {@code name} ("Outer.Inner") in {@code unit}, or a refusal: a top-level type,
   * or no such type.
   */
  private static AbstractTypeDeclaration find(CompilationUnit unit, String name, String rel) {
    String[] parts = name.split("\\.");
    AbstractTypeDeclaration at = Declarations.typePath(unit, new String[] {parts[0]});
    if (at != null && parts.length == 1) {
      String example = name + ".Inner";
      for (Object o : at.bodyDeclarations()) {
        if (o instanceof AbstractTypeDeclaration member) {
          example = name + "." + member.getName().getIdentifier();
          break;
        }
      }
      throw new Refusal(
          name
              + " is a top-level type already. move moves a nested type to its own file:"
              + " name it as "
              + example
              + ". To move it to another package, pass package.");
    }
    at = Declarations.typePath(unit, parts);
    if (at == null) {
      throw new Refusal("No nested type " + name + " in " + rel + ". Name it as Outer.Inner.");
    }
    return at;
  }

  /**
   * Adds to {@code edits} the changes that make {@code u}'s references to the type with key {@code
   * key} name it as a top-level type, and to {@code refs} those references.
   */
  private static void references(
      CompilationUnit u,
      String text,
      String key,
      String pkg,
      String simple,
      boolean samePackage,
      List<TextEdit> edits,
      List<ASTNode> refs,
      List<SimpleName> named) {
    u.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(SimpleName n) {
            if (!n.isDeclaration()
                && n.getLocationInParent() != QualifiedName.NAME_PROPERTY
                && n.resolveBinding() instanceof ITypeBinding t
                && t.getTypeDeclaration().getKey().equals(key)) {
              named.add(n);
            }
            return true;
          }

          @Override
          public boolean visit(PackageDeclaration p) {
            return false;
          }

          @Override
          public boolean visit(ImportDeclaration i) {
            if (!i.isStatic()
                && !i.isOnDemand()
                && i.resolveBinding() instanceof ITypeBinding t
                && t.getTypeDeclaration().getKey().equals(key)) {
              refs.add(i);
              if (samePackage) {
                edits.add(
                    new TextEdit(
                        Lines.start(text, i.getStartPosition()),
                        Lines.end(text, i.getStartPosition()),
                        ""));
              } else {
                edits.add(
                    new TextEdit(
                        i.getName().getStartPosition(),
                        i.getName().getStartPosition() + i.getName().getLength(),
                        qualified(pkg, simple)));
              }
            }
            return false;
          }

          @Override
          public boolean visit(QualifiedName q) {
            if (q.resolveBinding() instanceof ITypeBinding t
                && t.getTypeDeclaration().getKey().equals(key)) {
              refs.add(q);
              boolean short_ = q.getQualifier() instanceof SimpleName;
              edits.add(
                  new TextEdit(
                      q.getStartPosition(),
                      q.getStartPosition() + q.getLength(),
                      short_ ? simple : qualified(pkg, simple)));
              return false;
            }
            return true;
          }
        });
  }

  /**
   * Adds to {@code edits}, in the outer file {@code u}: the declaring type's name before each
   * unqualified use, inside the moved type, of a static member or member type it reaches through
   * the outer type (its own, an enclosing type's, or one they inherit), with the qualifiers' first
   * names put in {@code qualifiers}; and the removal of {@code private} from each member used
   * across the moved type's boundary, which {@code widened} names by position.
   */
  private static void qualifyAndWiden(
      CompilationUnit u,
      String text,
      AbstractTypeDeclaration decl,
      ITypeBinding type,
      String key,
      String outerKey,
      String outerLabel,
      String simple,
      List<TextEdit> edits,
      Map<Integer, String> widened,
      Set<String> accessed,
      Set<String> qualifiers) {
    int start = decl.getStartPosition();
    int end = start + decl.getLength();
    u.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            IBinding b = n.resolveBinding();
            if (b == null || n.isDeclaration()) {
              return true;
            }
            boolean inside = n.getStartPosition() >= start && n.getStartPosition() < end;
            String q = inside && unqualified(n) ? qualifier(b, type, outerLabel) : null;
            if (q != null) {
              edits.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), q + "."));
              qualifiers.add(q.contains(".") ? q.substring(0, q.indexOf('.')) : q);
            }
            if (!accessorCall(n, b, inside)) {
              widen(b, inside);
            }
            return true;
          }

          /**
           * Whether {@code b}, which {@code n} reads, is a record's field that the move puts on the
           * other side of the boundary from the read: the read, which can no longer see the private
           * field, becomes a call of the accessor, which means the same when the accessor only
           * returns the field.
           */
          private boolean accessorCall(SimpleName n, IBinding b, boolean inside) {
            if (!(b instanceof IVariableBinding v)
                || !v.isField()
                || Modifier.isStatic(v.getModifiers())
                || v.getDeclaringClass() == null
                || !v.getDeclaringClass().isRecord()) {
              return false;
            }
            ITypeBinding r = v.getDeclaringClass().getTypeDeclaration();
            ASTNode record = u.findDeclaringNode(r.getKey());
            if (record == null
                || (record.getStartPosition() >= start && record.getStartPosition() < end)
                    == inside) {
              return false;
            }
            String field = label(v.getVariableDeclaration(), key, outerKey, outerLabel, simple);
            if (!returnsField(u, r, v.getName())) {
              throw new Refusal(
                  (inside ? simple : outerLabel)
                      + " reads the field "
                      + field
                      + ", which is private once "
                      + simple
                      + " is top-level, and "
                      + field
                      + "() does more than return it. Replace each read with a call of "
                      + field
                      + "() if that is what it should mean, then move it again.");
            }
            int at = n.getStartPosition() + n.getLength();
            edits.add(new TextEdit(at, at, "()"));
            accessed.add(field);
            return true;
          }

          @Override
          public boolean visit(ClassInstanceCreation c) {
            IMethodBinding b = c.resolveConstructorBinding();
            if (b != null) {
              widen(b, c.getStartPosition() >= start && c.getStartPosition() < end);
            }
            return true;
          }

          private void widen(IBinding b, boolean inside) {
            IBinding d =
                b instanceof IMethodBinding m
                    ? m.getMethodDeclaration()
                    : b instanceof IVariableBinding v
                        ? v.getVariableDeclaration()
                        : b instanceof ITypeBinding t ? t.getTypeDeclaration() : b;
            if (!Modifier.isPrivate(d.getModifiers())) {
              return;
            }
            ASTNode at = u.findDeclaringNode(d.getKey());
            BodyDeclaration body =
                at instanceof VariableDeclarationFragment f
                        && f.getParent() instanceof FieldDeclaration fd
                    ? fd
                    : at instanceof BodyDeclaration bd ? bd : null;
            if (body == null || body == decl || widened.containsKey(body.getStartPosition())) {
              return;
            }
            boolean declaredInside =
                body.getStartPosition() >= start && body.getStartPosition() < end;
            if (declaredInside == inside) {
              return;
            }
            for (Object o : body.modifiers()) {
              if (o instanceof Modifier m && m.isPrivate()) {
                edits.add(dropModifier(text, m));
              }
            }
            widened.put(body.getStartPosition(), label(d, key, outerKey, outerLabel, simple));
          }
        });
  }

  /** Whether {@code n} names something without a qualifier before it. */
  private static boolean unqualified(SimpleName n) {
    StructuralPropertyDescriptor where = n.getLocationInParent();
    if (where == MethodInvocation.NAME_PROPERTY) {
      return ((MethodInvocation) n.getParent()).getExpression() == null;
    }
    return where != QualifiedName.NAME_PROPERTY
        && where != FieldAccess.NAME_PROPERTY
        && where != SuperFieldAccess.NAME_PROPERTY
        && where != SuperMethodInvocation.NAME_PROPERTY
        && where != QualifiedType.NAME_PROPERTY
        && where != ExpressionMethodReference.NAME_PROPERTY
        && where != TypeMethodReference.NAME_PROPERTY
        && where != SuperMethodReference.NAME_PROPERTY;
  }

  /**
   * The qualifier {@code b} needs once {@code moved} is top-level, or null for none: {@code b} is a
   * static method or field, or a member type other than {@code moved}, that {@code moved} reaches
   * without a qualifier only while it is nested, through the outer type, a type enclosing it, or a
   * supertype of either. The qualifier is the declaring type ({@code outerLabel} for the outer
   * type). What {@code moved} reaches through its own supertypes needs none.
   */
  private static String qualifier(IBinding b, ITypeBinding moved, String outerLabel) {
    ITypeBinding owner;
    if (b instanceof IMethodBinding m && Modifier.isStatic(m.getModifiers())) {
      owner = m.getDeclaringClass();
    } else if (b instanceof IVariableBinding v
        && v.isField()
        && Modifier.isStatic(v.getModifiers())) {
      owner = v.getDeclaringClass();
    } else if (b instanceof ITypeBinding t
        && t.getDeclaringClass() != null
        && !t.getTypeDeclaration().getKey().equals(moved.getTypeDeclaration().getKey())) {
      owner = t.getDeclaringClass();
    } else {
      return null;
    }
    String ownerKey = owner.getTypeDeclaration().getKey();
    if (Hierarchy.isSubtype(moved, ownerKey)) {
      return null;
    }
    ITypeBinding outer = moved.getDeclaringClass();
    if (declaredBy(outer, ownerKey)) {
      return outerLabel;
    }
    for (ITypeBinding t = outer; t != null; t = t.getDeclaringClass()) {
      if (Hierarchy.isSubtype(t, ownerKey)) {
        return Names.typeLabel(owner);
      }
    }
    return null;
  }

  private static boolean declaredBy(ITypeBinding type, String key) {
    return type != null && type.getTypeDeclaration().getKey().equals(key);
  }

  /** "Shop.label(String)", "Line.price": a widened member, its type named as after the move. */
  private static String label(
      IBinding d, String key, String outerKey, String outerLabel, String simple) {
    ITypeBinding owner =
        d instanceof IMethodBinding m
            ? m.getDeclaringClass()
            : d instanceof IVariableBinding v
                ? v.getDeclaringClass()
                : ((ITypeBinding) d).getDeclaringClass();
    String in =
        declaredBy(owner, key)
            ? simple
            : declaredBy(owner, outerKey) ? outerLabel : owner.getName();
    if (d instanceof IMethodBinding m) {
      List<String> types = new ArrayList<>();
      for (ITypeBinding t : m.getParameterTypes()) {
        types.add(t.getErasure().getName());
      }
      String params = "(" + String.join(", ", types) + ")";
      return m.isConstructor() ? in + params : in + "." + m.getName() + params;
    }
    return in + "." + d.getName();
  }

  /** The names used in {@code u} inside and outside {@code start}-{@code end}, imports left out. */
  private static void identifiers(
      CompilationUnit u, int start, int end, Set<String> inside, Set<String> outside) {
    u.accept(
        new ASTVisitor(true) {
          @Override
          public boolean visit(PackageDeclaration p) {
            return false;
          }

          @Override
          public boolean visit(ImportDeclaration i) {
            return false;
          }

          @Override
          public boolean visit(SimpleName n) {
            boolean in = n.getStartPosition() >= start && n.getStartPosition() < end;
            (in ? inside : outside).add(n.getIdentifier());
            return true;
          }
        });
  }

  /** Removes modifier {@code m} and the spaces after it. */
  private static TextEdit dropModifier(String text, Modifier m) {
    int end = m.getStartPosition() + m.getLength();
    while (end < text.length() && text.charAt(end) == ' ') {
      end++;
    }
    return new TextEdit(m.getStartPosition(), end, "");
  }

  /** Where {@code decl}'s text starts after its Javadoc and the spaces after it. */
  private static int afterJavadoc(String text, AbstractTypeDeclaration decl) {
    if (decl.getJavadoc() == null) {
      return decl.getStartPosition();
    }
    int at = decl.getJavadoc().getStartPosition() + decl.getJavadoc().getLength();
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at;
  }

  /** {@code text}, which starts at {@code offset} of the file, with {@code edits} applied. */
  private static String apply(String text, int offset, List<TextEdit> edits) {
    return TextEdit.apply(text, edits, offset);
  }

  /**
   * {@code moved} with the indentation of the declaration's first line ({@code start} in {@code
   * text}) taken off every line.
   */
  private static String dedent(String moved, String text, int start) {
    String indent = text.substring(Lines.start(text, start), start);
    if (!indent.isBlank() && !indent.isEmpty()) {
      return moved;
    }
    List<String> out = new ArrayList<>();
    for (String line : moved.split("\n", -1)) {
      out.add(line.startsWith(indent) ? line.substring(indent.length()) : line);
    }
    return String.join("\n", out);
  }

  private static String packageOf(CompilationUnit u) {
    return u.getPackage() == null ? "" : u.getPackage().getName().getFullyQualifiedName();
  }

  private static String qualified(String pkg, String simple) {
    return pkg.isEmpty() ? simple : pkg + "." + simple;
  }

  private static String simpleName(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  private static String plural(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  private static String joinAnd(List<String> items) {
    if (items.size() == 1) {
      return items.get(0);
    }
    return String.join(", ", items.subList(0, items.size() - 1))
        + " and "
        + items.get(items.size() - 1);
  }

  /**
   * Whether the accessor of record {@code r}'s component {@code name} is the generated one, or one
   * that only returns the field.
   */
  private static boolean returnsField(CompilationUnit u, ITypeBinding r, String name) {
    for (IMethodBinding m : r.getDeclaredMethods()) {
      if (!m.getName().equals(name) || m.getParameterTypes().length != 0) {
        continue;
      }
      if (m.isSyntheticRecordMethod()) {
        return true;
      }
      if (!(u.findDeclaringNode(m.getKey()) instanceof MethodDeclaration d)
          || d.getBody() == null
          || d.getBody().statements().size() != 1
          || !(d.getBody().statements().get(0) instanceof ReturnStatement ret)) {
        return false;
      }
      return (ret.getExpression() instanceof SimpleName s && s.getIdentifier().equals(name))
          || (ret.getExpression() instanceof FieldAccess f
              && f.getExpression() instanceof ThisExpression t
              && t.getQualifier() == null
              && f.getName().getIdentifier().equals(name));
    }
    return true;
  }

  /**
   * Adds to {@code edits} an explicit permits clause on each sealed type of {@code u} that has none
   * and whose permitted subtypes (the types of {@code u} that extend or implement it) the move from
   * {@code start}-{@code end} puts in another file than it, each named from its package as after
   * the move; a private subtype the clause names from the other file becomes package-private, in
   * {@code widened}. Names in {@code sealed} each type that gets a clause.
   */
  private static void permits(
      CompilationUnit u,
      String text,
      int start,
      int end,
      String pkg,
      String name,
      String simple,
      String key,
      String outerKey,
      String outerLabel,
      List<TextEdit> edits,
      Map<Integer, String> widened,
      List<String> sealed) {
    List<AbstractTypeDeclaration> types = Nodes.all(u, AbstractTypeDeclaration.class);
    for (AbstractTypeDeclaration a : types) {
      ITypeBinding t = a.resolveBinding();
      if (!(a instanceof TypeDeclaration td)
          || !Modifier.isSealed(td.getModifiers())
          || !td.permittedTypes().isEmpty()
          || t == null) {
        continue;
      }
      boolean in = a.getStartPosition() >= start && a.getStartPosition() < end;
      List<AbstractTypeDeclaration> subtypes = new ArrayList<>();
      boolean split = false;
      for (AbstractTypeDeclaration s : types) {
        ITypeBinding b = s.resolveBinding();
        if (b != null && extendsDirectly(b, t.getTypeDeclaration().getKey())) {
          subtypes.add(s);
          split |= (s.getStartPosition() >= start && s.getStartPosition() < end) != in;
        }
      }
      if (!split) {
        continue;
      }
      List<String> names = new ArrayList<>();
      for (AbstractTypeDeclaration s : subtypes) {
        ITypeBinding b = s.resolveBinding().getTypeDeclaration();
        names.add(movedName(b, pkg, name, simple));
        boolean sIn = s.getStartPosition() >= start && s.getStartPosition() < end;
        if (sIn != in
            && s.getStartPosition() != start
            && !widened.containsKey(s.getStartPosition())) {
          for (Object o : s.modifiers()) {
            if (o instanceof Modifier m && m.isPrivate()) {
              edits.add(dropModifier(text, m));
              widened.put(s.getStartPosition(), label(b, key, outerKey, outerLabel, simple));
            }
          }
        }
      }
      int at = headerEnd(text, td);
      edits.add(new TextEdit(at, at, " permits " + String.join(", ", names)));
      sealed.add(movedName(t.getTypeDeclaration(), pkg, name, simple));
    }
  }

  /** Whether {@code b}'s superclass or one of its interfaces is the type with key {@code key}. */
  private static boolean extendsDirectly(ITypeBinding b, String key) {
    if (declaredBy(b.getSuperclass(), key)) {
      return true;
    }
    for (ITypeBinding i : b.getInterfaces()) {
      if (declaredBy(i, key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code b}'s name from its package once the type {@code name} ("Outer.Inner") is top-level as
   * {@code simple}: "Outer.Other", "Inner", "Inner.Nested".
   */
  private static String movedName(ITypeBinding b, String pkg, String name, String simple) {
    String q = b.getQualifiedName();
    String rel = pkg.isEmpty() ? q : q.substring(pkg.length() + 1);
    return rel.equals(name) || rel.startsWith(name + ".")
        ? simple + rel.substring(name.length())
        : rel;
  }

  /** Where {@code t}'s header ends: after its last supertype, type parameters or name. */
  private static int headerEnd(String text, TypeDeclaration t) {
    if (!t.superInterfaceTypes().isEmpty()) {
      return Nodes.end((ASTNode) t.superInterfaceTypes().get(t.superInterfaceTypes().size() - 1));
    }
    if (t.getSuperclassType() != null) {
      return Nodes.end(t.getSuperclassType());
    }
    if (!t.typeParameters().isEmpty()) {
      return text.indexOf(
              '>', Nodes.end((ASTNode) t.typeParameters().get(t.typeParameters().size() - 1)))
          + 1;
    }
    return Nodes.end(t.getName());
  }
}
