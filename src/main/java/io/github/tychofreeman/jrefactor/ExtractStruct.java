package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.RecordPattern;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * extract_struct with parameters (introduce parameter object): consecutive parameters of a method
 * become one parameter of a new record, in the method and every declaration of its family; each
 * body reads the record's components where it read the parameters, and every call passes {@code new
 * Record(...)} of its arguments.
 */
final class ExtractStruct {

  private ExtractStruct() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    if (args.has("fields")) {
      return fields(project, args);
    }
    String struct = args.required("struct");
    String parameter = args.required("parameter");
    List<String> chosen = args.list("parameters");
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isIdentifier(struct) && !SourceVersion.isKeyword(struct),
            () -> struct + " is not a valid name for the record."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(parameter) && !SourceVersion.isKeyword(parameter),
            () -> parameter + " is not a valid name for the new parameter."),
        Precondition.that(
            () -> !chosen.isEmpty(), () -> "parameters names the parameters to group."));
    String rel = args.required("file");
    Path record =
        project.javaFile(rel).toAbsolutePath().normalize().resolveSibling(struct + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(record),
            () -> project.relative(record) + " already exists. Choose another name."));
    MethodFamily family = MethodFamily.of(project, args);
    List<Integer> at = new ArrayList<>();
    for (String p : chosen) {
      at.add(family.index(p, "parameters", false));
    }
    Collections.sort(at);
    int first = at.get(0);
    int last = at.get(at.size() - 1);
    Precondition.require(
        Precondition.that(
            () -> last - first == at.size() - 1,
            () ->
                Extract.and(chosen)
                    + " are not next to each other in "
                    + family.label
                    + ", and grouped at one place their arguments would run in another order."
                    + " Move them together first (move_parameter)."),
        Precondition.that(
            () -> family.lambdas.isEmpty(),
            () ->
                "lambdas implement "
                    + family.label
                    + ", whose parameters extract_struct does not group yet. Write them as methods"
                    + " first."));

    List<String> components = new ArrayList<>();
    for (int i = first; i <= last; i++) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) family.target.parameters().get(i);
      Precondition.require(
          Precondition.that(
              () -> !p.isVarargs(),
              () -> p.getName() + " is varargs, which a record cannot hold."));
      String unusable = unusable(p.resolveBinding().getType());
      Precondition.require(
          Precondition.that(
              () -> unusable == null,
              () ->
                  "the type of "
                      + p.getName()
                      + " "
                      + unusable
                      + ", which the new record in its own file cannot name. Leave it out."));
      components.add(
          Extract.typeName(p.resolveBinding().getType()) + " " + p.getName().getIdentifier());
    }

    SignatureChange change = new SignatureChange(family);
    for (Signatures.Decl d : family.decls) {
      List<?> params = d.m().parameters();
      change.replaceRange(
          d.file(),
          ((ASTNode) params.get(first)).getStartPosition(),
          Nodes.end(((ASTNode) params.get(last))),
          struct + " " + parameter);
      if (d.m().getBody() == null) {
        continue;
      }
      List<IVariableBinding> grouped = new ArrayList<>();
      for (int i = first; i <= last; i++) {
        grouped.add(((SingleVariableDeclaration) params.get(i)).resolveBinding());
      }
      d.m()
          .getBody()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(SimpleName n) {
                  if (n.getIdentifier().equals(parameter)) {
                    throw new Refusal(
                        "the body of "
                            + family.label
                            + " already names "
                            + parameter
                            + ". Choose another name for the new parameter.");
                  }
                  if (!(n.resolveBinding() instanceof IVariableBinding v)
                      || grouped.stream().noneMatch(g -> g != null && g.isEqualTo(v))) {
                    return true;
                  }
                  ASTNode p = n.getParent();
                  if ((p instanceof Assignment a && a.getLeftHandSide() == n)
                      || p instanceof PostfixExpression
                      || (p instanceof PrefixExpression pre
                          && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                              || pre.getOperator() == PrefixExpression.Operator.DECREMENT))) {
                    throw new Refusal(
                        "the body of "
                            + family.label
                            + " assigns "
                            + n
                            + ", which as a record's component cannot be. Assign a local instead"
                            + " first.");
                  }
                  change.replaceRange(
                      d.file(),
                      n.getStartPosition(),
                      Nodes.end(n),
                      parameter + "." + n.getIdentifier() + "()");
                  return true;
                }
              });
    }
    for (Signatures.Call c : family.calls) {
      List<?> arguments = c.arguments();
      int s = ((ASTNode) arguments.get(first)).getStartPosition();
      int e = Nodes.end(((ASTNode) arguments.get(last)));
      change.replaceRange(
          c.file(), s, e, "new " + struct + "(" + c.file().text().substring(s, e) + ")");
    }

    Opened home = Opened.of(project, rel);
    String pkg =
        home.unit().getPackage() == null
            ? ""
            : "package " + home.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String text = pkg + "public record " + struct + "(" + String.join(", ", components) + ") {}\n";
    Gate.applyOrRefuse(
        project,
        Map.of(record, Imports.fix(project, record, "", text, List.of()).text()),
        "creating " + struct,
        "Nothing was changed.");
    return change.apply(
        project,
        "grouped "
            + Extract.and(chosen)
            + " of "
            + family.label
            + " into "
            + struct
            + " "
            + parameter
            + " ("
            + project.relative(record)
            + ")",
        args.list("imports"));
  }

  /**
   * Why {@code t} cannot be a component of a record in a file of its own, in words ("uses the type
   * variable T", "uses the private type Outer.Shift"), or null when it can.
   */
  private static String unusable(ITypeBinding t) {
    if (t == null) {
      return "does not resolve";
    }
    if (t.isArray()) {
      return unusable(t.getElementType());
    }
    if (t.isTypeVariable() || t.isCapture()) {
      return "uses the type variable " + t.getName();
    }
    if (t.isWildcardType()) {
      return t.getBound() == null ? null : unusable(t.getBound());
    }
    if (t.isLocal() || t.isAnonymous()) {
      return "uses the local type " + t.getName();
    }
    for (ITypeBinding m = t; m != null; m = m.getDeclaringClass()) {
      if (Modifier.isPrivate(m.getModifiers())) {
        return "uses the private type " + Names.typeLabel(m);
      }
    }
    for (ITypeBinding arg : t.getTypeArguments()) {
      String why = unusable(arg);
      if (why != null) {
        return why;
      }
    }
    return null;
  }

  /**
   * extract_struct with fields (extract class): the named instance fields move into a new
   * package-private class in a file of its own, held by one {@code private final} field of the
   * class in the first one's place; every use goes through it ({@code home.street}).
   */
  private static Outcome fields(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String struct = args.required("struct");
    String holder = args.required("field");
    List<String> chosen = args.list("fields");
    Precondition.require(
        Precondition.that(
            () -> SourceVersion.isIdentifier(struct) && !SourceVersion.isKeyword(struct),
            () -> struct + " is not a valid name for the class."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(holder) && !SourceVersion.isKeyword(holder),
            () -> holder + " is not a valid name for the field that holds it."),
        Precondition.that(() -> !chosen.isEmpty(), () -> "fields names the fields to move."));
    Opened opened = Opened.of(project, rel);
    for (Object t : opened.unit().types()) {
      if (t instanceof RecordDeclaration r
          && r.getName().getIdentifier().equals(args.required("name"))
          && r.resolveBinding() != null) {
        return components(project, args, opened, r);
      }
    }
    String text = opened.text();
    TypeDeclaration owner = opened.topLevel(args.required("name"));
    String ownerName = owner.getName().getIdentifier();
    Path target = opened.file().resolveSibling(struct + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(target),
            () -> project.relative(target) + " already exists. Choose another name."));
    List<FieldDeclaration> moved = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    for (String f : chosen) {
      FieldDeclaration fd = null;
      for (FieldDeclaration d : owner.getFields()) {
        for (Object o : d.fragments()) {
          if (((VariableDeclarationFragment) o).getName().getIdentifier().equals(f)) {
            fd = d;
          }
        }
      }
      FieldDeclaration decl = fd;
      Precondition.require(
          Precondition.that(() -> decl != null, () -> f + " is not a field of " + ownerName + "."),
          Precondition.that(
              () -> decl.fragments().size() == 1,
              () -> "the declaration of " + f + " declares several fields. Split it first."),
          Precondition.that(
              () -> !Modifier.isStatic(decl.getModifiers()),
              () -> f + " is static; extract_struct moves instance fields."));
      VariableDeclarationFragment frag = (VariableDeclarationFragment) decl.fragments().get(0);
      Expression init = frag.getInitializer();
      Precondition.require(
          Precondition.that(
              () -> !(Modifier.isFinal(decl.getModifiers()) && init == null),
              () ->
                  f
                      + " is final and set by a constructor, which could not set it through the"
                      + " holder. Give it an initializer, or leave it out."),
          Precondition.that(
              () -> init == null || initializedAlone(init),
              () ->
                  "the initializer of "
                      + f
                      + " reads a field, a local or a call, and in the new class it would run when"
                      + " the holder is made, not in its own place. Leave it out, or make it a"
                      + " constant first."));
      moved.add(decl);
      keys.add(frag.resolveBinding().getVariableDeclaration().getKey());
    }
    moved.sort(Comparator.comparingInt(ASTNode::getStartPosition));
    int ownerStart = owner.getStartPosition();
    int ownerEnd = ownerStart + owner.getLength();
    Map<String, Set<Integer>> outside = new TreeMap<>();
    for (String f : chosen) {
      Holding holding = Holding.of(project, f, opened.file());
      for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
        boolean home = e.getKey().equals(opened.file());
        CompilationUnit u = e.getValue();
        u.accept(
            new ASTVisitor() {
              @Override
              public boolean visit(SimpleName n) {
                if (!n.isDeclaration()
                    && n.resolveBinding() instanceof IVariableBinding v
                    && keys.contains(v.getVariableDeclaration().getKey())
                    && (!home
                        || n.getStartPosition() < ownerStart
                        || n.getStartPosition() >= ownerEnd)) {
                  outside
                      .computeIfAbsent(
                          n.getIdentifier() + " is used in " + project.relative(e.getKey()),
                          k -> new TreeSet<>())
                      .add(u.getLineNumber(n.getStartPosition()));
                }
                return true;
              }
            });
      }
    }
    if (!outside.isEmpty()) {
      List<String> each = new ArrayList<>();
      outside.forEach((where, lines) -> each.add(where + " " + Lines.list(new ArrayList<>(lines))));
      throw new Refusal(
          String.join("; ", each)
              + ", and extract_struct moves only fields their class alone uses. Encapsulate them"
              + " first (encapsulate_field), or leave them out.");
    }
    List<TextEdit> edits = new ArrayList<>();
    owner.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (n.getIdentifier().equals(holder)) {
              throw new Refusal(
                  ownerName + " already names " + holder + ". Choose another name for the holder.");
            }
            if (!n.isDeclaration()
                && n.resolveBinding() instanceof IVariableBinding v
                && keys.contains(v.getVariableDeclaration().getKey())) {
              edits.add(new TextEdit(n.getStartPosition(), n.getStartPosition(), holder + "."));
            }
            return true;
          }
        });
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < moved.size(); i++) {
      FieldDeclaration fd = moved.get(i);
      int start = Lines.start(text, fd.getStartPosition());
      String fieldText = text.substring(fd.getStartPosition(), Nodes.end(fd));
      for (Object o : fd.modifiers()) {
        if (o instanceof Modifier m && m.isPrivate()) {
          int from = m.getStartPosition() - fd.getStartPosition();
          int to = Nodes.end(m) - fd.getStartPosition();
          while (to < fieldText.length() && fieldText.charAt(to) == ' ') {
            to++;
          }
          fieldText = fieldText.substring(0, from) + fieldText.substring(to);
        }
      }
      body.append("  ").append(fieldText).append("\n");
      if (i == 0) {
        String indent = text.substring(start, fd.getStartPosition());
        edits.add(
            new TextEdit(
                start,
                Lines.end(text, Nodes.end(fd) - 1),
                indent + "private final " + struct + " " + holder + " = new " + struct + "();\n"));
      } else {
        edits.add(MemberMove.removal(text, fd));
      }
    }
    String pkg =
        opened.unit().getPackage() == null
            ? ""
            : "package " + opened.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String created = pkg + "class " + struct + " {\n" + body + "}\n";
    Gate.applyOrRefuse(
        project,
        Map.of(target, Imports.fix(project, target, "", created, List.of()).text()),
        "creating " + struct,
        "Nothing was changed.");
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            TextEdit.apply(text, edits, 0),
            args.list("imports"),
            "moving fields into " + struct,
            "Nothing was changed.");
    return new Outcome(
        "moved "
            + Extract.and(chosen)
            + " of "
            + ownerName
            + " into "
            + struct
            + " ("
            + project.relative(target)
            + "), held in "
            + holder
            + written.fixed().said(),
        written.declared());
  }

  /**
   * extract_struct with fields on a record: the named components, next to each other, become one
   * component of a new record in a file of its own. Every construction passes the new record in
   * their place, and every accessor call and read of them goes through it ({@code
   * o.address().street()}).
   */
  private static Outcome components(
      SourceProject project, Args args, Opened opened, RecordDeclaration owner) throws Exception {
    String struct = args.required("struct");
    String holder = args.required("field");
    List<String> chosen = args.list("fields");
    String text = opened.text();
    String ownerName = owner.getName().getIdentifier();
    String key = owner.resolveBinding().getErasure().getKey();
    Path target = opened.file().resolveSibling(struct + ".java");
    Precondition.require(
        Precondition.that(
            () -> !Files.exists(target),
            () -> project.relative(target) + " already exists. Choose another name."));
    List<?> all = owner.recordComponents();
    List<Integer> at = new ArrayList<>();
    for (String f : chosen) {
      int found = -1;
      for (int k = 0; k < all.size(); k++) {
        if (((SingleVariableDeclaration) all.get(k)).getName().getIdentifier().equals(f)) {
          found = k;
        }
      }
      int index = found;
      Precondition.require(
          Precondition.that(
              () -> index >= 0, () -> f + " is not a component of " + ownerName + "."));
      at.add(index);
    }
    Collections.sort(at);
    int first = at.get(0);
    int last = at.get(at.size() - 1);
    MethodDeclaration constructor = null;
    boolean definesToString = false;
    Set<String> names = new TreeSet<>();
    for (Object o : all) {
      names.add(((SingleVariableDeclaration) o).getName().getIdentifier());
    }
    for (Object o : owner.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m) {
        names.add(m.getName().getIdentifier());
        if (m.isConstructor() && constructor == null) {
          constructor = m;
        }
        if (m.getName().getIdentifier().equals("toString") && m.parameters().isEmpty()) {
          definesToString = true;
        }
      }
    }
    MethodDeclaration explicit = constructor;
    boolean printed = definesToString || args.flag("value_semantics");
    Precondition.require(
        Precondition.that(
            () -> last - first == at.size() - 1,
            () ->
                Extract.and(chosen)
                    + " are not next to each other in "
                    + ownerName
                    + ", and grouped at one place the arguments of each construction would run"
                    + " in another order. Group components that are next to each other."),
        Precondition.that(
            () -> explicit == null,
            () ->
                ownerName
                    + " declares a constructor (line "
                    + opened.unit().getLineNumber(explicit.getStartPosition())
                    + "), whose parameters and assignments extract_struct does not rewrite yet."),
        Precondition.that(
            () -> !names.contains(holder) || chosen.contains(holder),
            () -> ownerName + " already names " + holder + ". Choose another name for the holder."),
        Precondition.that(
            () -> printed,
            () ->
                ownerName
                    + "'s generated toString would print "
                    + holder
                    + "="
                    + struct
                    + "[...] where it printed "
                    + Extract.and(chosen)
                    + ". Pass value_semantics=true to accept that, or define toString first."));
    List<String> components = new ArrayList<>();
    for (int i = first; i <= last; i++) {
      SingleVariableDeclaration c = (SingleVariableDeclaration) all.get(i);
      String unusable = unusable(c.resolveBinding().getType());
      Precondition.require(
          Precondition.that(
              () -> !c.isVarargs(),
              () -> c.getName() + " is varargs, which stays last. Leave it out."),
          Precondition.that(
              () -> unusable == null,
              () ->
                  "the type of "
                      + c.getName()
                      + " "
                      + unusable
                      + ", which the new record in its own file cannot name. Leave it out."));
      components.add(text.substring(c.getStartPosition(), Nodes.end(c)));
    }

    Map<Path, String> texts = new LinkedHashMap<>();
    for (Path f : project.javaFiles()) {
      String t = Files.readString(f);
      if (f.equals(opened.file())
          || t.contains(ownerName)
          || chosen.stream().anyMatch(t::contains)) {
        texts.put(f, t);
      }
    }
    Map<Path, CompilationUnit> units = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    List<String> refused = new ArrayList<>();
    for (Map.Entry<Path, CompilationUnit> e : units.entrySet()) {
      Path file = e.getKey();
      CompilationUnit u = e.getValue();
      String t = texts.get(file);
      List<TextEdit> here = new ArrayList<>();
      String where = project.relative(file) + " line ";
      for (ClassInstanceCreation c : Nodes.all(u, ClassInstanceCreation.class)) {
        IMethodBinding b = c.resolveConstructorBinding();
        if (b != null && b.getDeclaringClass().getErasure().getKey().equals(key)) {
          List<?> arguments = c.arguments();
          int s = ((ASTNode) arguments.get(first)).getStartPosition();
          here.add(new TextEdit(s, s, "new " + struct + "("));
          int end = Nodes.end((ASTNode) arguments.get(last));
          here.add(new TextEdit(end, end, ")"));
        }
      }
      for (MethodInvocation m : Nodes.all(u, MethodInvocation.class)) {
        IMethodBinding b = m.resolveMethodBinding();
        if (b != null
            && b.getDeclaringClass().getErasure().getKey().equals(key)
            && m.arguments().isEmpty()
            && chosen.contains(b.getName())) {
          int s = m.getName().getStartPosition();
          here.add(new TextEdit(s, s, holder + "()."));
        }
      }
      for (SimpleName n : Nodes.all(u, SimpleName.class)) {
        if (!n.isDeclaration()
            && n.resolveBinding() instanceof IVariableBinding v
            && v.isField()
            && v.getDeclaringClass() != null
            && v.getDeclaringClass().getErasure().getKey().equals(key)
            && chosen.contains(v.getName())) {
          here.add(
              new TextEdit(
                  n.getStartPosition(), Nodes.end(n), holder + "." + n.getIdentifier() + "()"));
        }
      }
      for (MethodReference r : Nodes.all(u, MethodReference.class)) {
        IMethodBinding b = r.resolveMethodBinding();
        if (b != null
            && b.getDeclaringClass().getErasure().getKey().equals(key)
            && (b.isConstructor() || chosen.contains(b.getName()))) {
          refused.add(
              t.substring(r.getStartPosition(), Nodes.end(r))
                  + " ("
                  + where
                  + u.getLineNumber(r.getStartPosition())
                  + ")");
        }
      }
      for (RecordPattern p : Nodes.all(u, RecordPattern.class)) {
        ITypeBinding b = p.getPatternType().resolveBinding();
        if (b != null && b.getErasure().getKey().equals(key)) {
          refused.add(
              "the pattern "
                  + t.substring(p.getStartPosition(), Nodes.end(p))
                  + " ("
                  + where
                  + u.getLineNumber(p.getStartPosition())
                  + ")");
        }
      }
      if (file.equals(opened.file())) {
        int s = ((ASTNode) all.get(first)).getStartPosition();
        here.add(new TextEdit(s, Nodes.end((ASTNode) all.get(last)), struct + " " + holder));
      }
      if (!here.isEmpty()) {
        edits.put(file, here);
      }
    }
    if (!refused.isEmpty()) {
      throw new Refusal(
          String.join(", ", refused)
              + " would need the components it names apart, which extract_struct does not rewrite"
              + " yet. Write it as a lambda or with the accessors first.");
    }

    String pkg =
        opened.unit().getPackage() == null
            ? ""
            : "package " + opened.unit().getPackage().getName().getFullyQualifiedName() + ";\n\n";
    String visibility = Modifier.isPublic(owner.getModifiers()) ? "public " : "";
    String created =
        pkg + visibility + "record " + struct + "(" + String.join(", ", components) + ") {}\n";
    Gate.applyOrRefuse(
        project,
        Map.of(target, Imports.fix(project, target, "", created, List.of()).text()),
        "creating " + struct,
        "Nothing was changed.");
    Map<Path, String> changed = new LinkedHashMap<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      String before = texts.get(e.getKey());
      String after = TextEdit.apply(before, e.getValue(), 0);
      changed.put(
          e.getKey(), Imports.fix(project, e.getKey(), before, after, args.list("imports")).text());
    }
    Stubs.Declared declared =
        Gate.applyOrRefuse(
            project, changed, "moving components into " + struct, "Nothing was changed.");
    List<String> files = new ArrayList<>();
    for (Path f : changed.keySet()) {
      files.add(project.relative(f));
    }
    return new Outcome(
        "moved "
            + Extract.and(chosen)
            + " of "
            + ownerName
            + " into "
            + struct
            + " ("
            + project.relative(target)
            + "), held in "
            + holder
            + "; changed "
            + String.join(", ", files),
        declared);
  }

  /** Whether an initializer can run when the holder is made: constants and new objects of them. */
  private static boolean initializedAlone(Expression init) {
    for (Effects.Effect e : Effects.of(init).all()) {
      switch (e.kind()) {
        case NAME -> {
          IVariableBinding v = e.variable();
          if (!(v.isField()
              && Modifier.isStatic(v.getModifiers())
              && Modifier.isFinal(v.getModifiers()))) {
            return false;
          }
        }
        case CREATE, NEW_ARRAY, MAY_THROW -> {}
        default -> {
          return false;
        }
      }
    }
    return true;
  }
}
