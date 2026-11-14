package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * subclass_to_strategy: a subclass that only overrides one method of its superclass becomes a
 * function the superclass holds (#332). The override's body and the superclass's own become static
 * methods of the superclass; its constructors take the function, the old ones passing its own;
 * {@code new Sub(...)} becomes {@code new Base(..., Base::methodSub)}, and the subclass's file is
 * deleted.
 */
final class SubclassToStrategy {

  private SubclassToStrategy() {}

  static Outcome run(SourceProject project, String rel, String subName, String method, String type)
      throws Exception {
    Opened opened = Opened.of(project, rel);
    TypeDeclaration first = opened.topLevel(subName);
    ITypeBinding baseType = first.resolveBinding().getSuperclass();
    Path baseFile = baseType == null ? null : TypeSwitch.fileOf(project, baseType);
    Precondition.require(
        Precondition.that(
            () -> !first.isInterface() && first.getSuperclassType() != null && baseFile != null,
            () ->
                subName
                    + " does not extend a top-level class of the project in a file of its own."),
        Precondition.that(
            () -> !baseFile.equals(opened.file()),
            () -> subName + " and its superclass are in one file. Move one to its own first."),
        Precondition.that(
            () ->
                first.typeParameters().isEmpty()
                    && !baseType.isGenericType()
                    && !baseType.isParameterizedType(),
            () -> subName + " or its superclass has type parameters, which is not built yet."));
    String baseName = baseType.getName();
    Holding holding = Holding.of(project, subName, baseFile);
    CompilationUnit subUnit = holding.units().get(opened.file());
    CompilationUnit baseUnit = holding.units().get(baseFile);
    String subText = holding.texts().get(opened.file());
    String baseText = holding.texts().get(baseFile);
    TypeDeclaration sub = MemberMove.typeNamed(subUnit, subName);
    TypeDeclaration base = MemberMove.typeNamed(baseUnit, baseName);
    String subKey = sub.resolveBinding().getKey();

    // The subclass: its constructors and the one override.
    MethodDeclaration override = null;
    List<MethodDeclaration> subConstructors = new ArrayList<>();
    for (Object o : sub.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m && m.isConstructor()) {
        subConstructors.add(m);
      } else if (o instanceof MethodDeclaration m
          && m.getName().getIdentifier().equals(method)
          && override == null) {
        override = m;
      } else {
        throw new Refusal(
            subName
                + " declares more than its constructors and "
                + method
                + " (line "
                + subUnit.getLineNumber(((ASTNode) o).getStartPosition())
                + "); only a subclass that differs by one method becomes a function.");
      }
    }
    if (override == null) {
      throw new Refusal(subName + " does not declare " + method + ".");
    }
    MethodDeclaration over = override;
    MethodDeclaration baseMethod = null;
    for (MethodDeclaration m : base.getMethods()) {
      if (m.resolveBinding() != null && over.resolveBinding().overrides(m.resolveBinding())) {
        baseMethod = m;
      }
    }
    MethodDeclaration own = baseMethod;
    Precondition.require(
        Precondition.that(
            () -> own != null && own.getBody() != null,
            () ->
                baseName
                    + " has no "
                    + method
                    + " with a body that "
                    + subName
                    + "'s overrides (an abstract one has no behavior of its own to keep)."),
        Precondition.that(
            () -> over.typeParameters().isEmpty(),
            () -> method + " has type parameters, which a function type cannot carry."));
    for (CompilationUnit u : holding.units().values()) {
      for (TypeDeclaration t : Nodes.all(u, TypeDeclaration.class)) {
        if (t.getSuperclassType() != null
            && t.getSuperclassType().resolveBinding() != null
            && t.getSuperclassType().resolveBinding().getKey().equals(subKey)) {
          throw new Refusal(t.getName() + " extends " + subName + ", which would be deleted.");
        }
      }
    }
    usesInstance(subUnit, over, subName + "." + method);
    usesInstance(baseUnit, own, baseName + "." + method);

    // Names.
    String cap = Character.toUpperCase(method.charAt(0)) + method.substring(1);
    String suffix =
        subName.endsWith(baseName) && subName.length() > baseName.length()
            ? subName.substring(0, subName.length() - baseName.length())
            : subName;
    String subFn = method + suffix;
    String ownFn = "default" + cap;
    String field = method;
    for (Object o : base.bodyDeclarations()) {
      List<String> names = new ArrayList<>();
      if (o instanceof MethodDeclaration m) {
        names.add(m.getName().getIdentifier());
      } else if (o instanceof FieldDeclaration f) {
        for (Object x : f.fragments()) {
          String n = ((VariableDeclarationFragment) x).getName().getIdentifier();
          if (n.equals(field)) {
            field = method + "Function";
          }
        }
      } else if (o instanceof TypeDeclaration t) {
        names.add(t.getName().getIdentifier());
      }
      for (String n : names) {
        if (n.equals(subFn) || n.equals(ownFn) || (type == null && n.equals(cap))) {
          throw new Refusal(
              baseName
                  + " already declares "
                  + n
                  + ", the name the tool would write. Rename it first"
                  + (n.equals(cap) ? ", or pass type." : "."));
        }
      }
    }
    boolean wide = Modifier.isPublic(sub.getModifiers());
    String vis = wide ? "public " : "";
    String params = paramsText(baseText, own);
    String thrown = thrownText(baseText, own);
    String ret = src(baseText, own.getReturnType2());
    String fnType = type == null ? cap : type;
    String sam = type == null ? method : samOf(project, baseFile, baseText, base, type, own);
    String indent = Indent.member(baseText, base, 2);
    String step = " ".repeat(Indent.step(baseText, 2));
    String in = indent + step;

    List<TextEdit> edits = new ArrayList<>();
    // The interface and the field.
    List<?> members = base.bodyDeclarations();
    StringBuilder head = new StringBuilder();
    if (type == null) {
      head.append(indent).append(vis).append("interface ").append(cap).append(" {\n");
      head.append(in).append(ret).append(' ').append(method).append('(').append(params);
      head.append(')').append(thrown).append(";\n").append(indent).append("}\n\n");
    }
    String fieldLine = indent + "private final " + fnType + " " + field + ";\n";
    FieldDeclaration lastField = null;
    for (FieldDeclaration f : base.getFields()) {
      if (!Modifier.isStatic(f.getModifiers())) {
        lastField = f;
      }
    }
    int top =
        members.isEmpty()
            ? Lines.end(baseText, base.getName().getStartPosition())
            : Lines.start(baseText, ((ASTNode) members.get(0)).getStartPosition());
    List<MethodDeclaration> constructors = new ArrayList<>();
    for (MethodDeclaration m : base.getMethods()) {
      if (m.isConstructor()) {
        constructors.add(m);
      }
    }
    String made = "";
    if (constructors.isEmpty()) {
      String v = Modifier.isPublic(base.getModifiers()) ? "public " : "";
      made =
          "\n" + indent + v + baseName + "(" + fnType + " " + field + ") {\n" + in + "this." + field
              + " = " + field + ";\n" + indent + "}\n\n" + indent + v + baseName + "() {\n" + in
              + "this(" + baseName + "::" + ownFn + ");\n" + indent + "}\n";
    }
    if (lastField == null) {
      edits.add(new TextEdit(top, top, head + fieldLine + made + (members.isEmpty() ? "" : "\n")));
    } else {
      if (head.length() > 0) {
        edits.add(new TextEdit(top, top, head.toString()));
      }
      int after = Lines.end(baseText, Nodes.end(lastField) - 1);
      edits.add(new TextEdit(after, after, fieldLine + made));
    }
    // Each constructor takes the function; one with the old parameters passes the class's own.
    for (MethodDeclaration c : constructors) {
      List<String> names = new ArrayList<>();
      for (Object p : c.parameters()) {
        SingleVariableDeclaration v = (SingleVariableDeclaration) p;
        if (v.isVarargs()) {
          throw new Refusal(
              "a constructor of "
                  + baseName
                  + " takes varargs, so it cannot take the function"
                  + " last. Give it an array parameter first.");
        }
        names.add(v.getName().getIdentifier());
      }
      for (SimpleName n : Nodes.all(c, SimpleName.class)) {
        if (n.getIdentifier().equals(field)
            && n.resolveBinding() instanceof IVariableBinding v
            && !v.isField()) {
          throw new Refusal(
              "a constructor of " + baseName + " already has a variable named " + field + ".");
        }
      }
      int close = paramsEnd(baseText, c);
      edits.add(new TextEdit(close, close, (names.isEmpty() ? "" : ", ") + fnType + " " + field));
      List<?> statements = c.getBody().statements();
      Object firstStatement = statements.isEmpty() ? null : statements.get(0);
      if (firstStatement instanceof ConstructorInvocation call) {
        int at = Nodes.end(call) - 2;
        edits.add(new TextEdit(at, at, (call.arguments().isEmpty() ? "" : ", ") + field));
      } else {
        String assign = in + "this." + field + " = " + field + ";\n";
        int at =
            firstStatement instanceof SuperConstructorInvocation s
                ? Lines.end(baseText, Nodes.end(s) - 1)
                : Lines.end(baseText, c.getBody().getStartPosition());
        edits.add(new TextEdit(at, at, assign));
      }
      String header = baseText.substring(headerStart(baseText, c), c.getBody().getStartPosition());
      names.add(baseName + "::" + ownFn);
      int end = Lines.end(baseText, Nodes.end(c) - 1);
      edits.add(
          new TextEdit(
              end,
              end,
              "\n"
                  + indent
                  + header
                  + "{\n"
                  + in
                  + "this("
                  + String.join(", ", names)
                  + ");\n"
                  + indent
                  + "}\n"));
    }
    // The method calls the function; its body and the override's become static methods.
    List<String> args = new ArrayList<>();
    for (Object p : own.parameters()) {
      args.add(((SingleVariableDeclaration) p).getName().getIdentifier());
    }
    boolean isVoid = ret.equals("void");
    String call = field + "." + sam + "(" + String.join(", ", args) + ");";
    String ownBody = src(baseText, own.getBody());
    String overBody = src(subText, over.getBody());
    edits.add(
        new TextEdit(
            own.getBody().getStartPosition(),
            Lines.end(baseText, Nodes.end(own) - 1),
            "{\n"
                + in
                + (isVoid ? "" : "return ")
                + call
                + "\n"
                + indent
                + "}\n\n"
                + indent
                + "private static "
                + ret
                + " "
                + ownFn
                + "("
                + params
                + ")"
                + thrown
                + " "
                + ownBody
                + "\n\n"
                + indent
                + vis
                + "static "
                + src(subText, over.getReturnType2())
                + " "
                + subFn
                + "("
                + paramsText(subText, over)
                + ")"
                + thrownText(subText, over)
                + " "
                + Indent.restep(overBody, indent, Indent.step(baseText, 2))
                + "\n"));

    // Every use of the subclass.
    Map<Path, List<TextEdit>> byFile = new HashMap<>();
    byFile.put(baseFile, edits);
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      if (e.getKey().equals(opened.file())) {
        continue;
      }
      String text = holding.texts().get(e.getKey());
      List<TextEdit> es = byFile.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
      for (SimpleName n : Nodes.all(e.getValue(), SimpleName.class)) {
        if (!(n.resolveBinding() instanceof ITypeBinding t) || !t.getKey().equals(subKey)) {
          continue;
        }
        int line = e.getValue().getLineNumber(n.getStartPosition());
        String where = project.relative(e.getKey()) + " line " + line;
        ImportDeclaration imp = ancestor(n, ImportDeclaration.class);
        if (imp != null) {
          es.add(
              new TextEdit(
                  Lines.start(text, imp.getStartPosition()),
                  Lines.end(text, Nodes.end(imp) - 1),
                  ""));
          continue;
        }
        if (!(n.getParent() instanceof SimpleType st)) {
          throw new Refusal(
              subName
                  + " is named at "
                  + where
                  + " other than as a type (a static member, a"
                  + " qualifier), which would have nothing to name.");
        }
        String kind =
            ancestor(st, InstanceofExpression.class) != null
                    || ancestor(st, PatternInstanceofExpression.class) != null
                ? "an instanceof"
                : st.getParent() instanceof CastExpression
                    ? "a cast"
                    : st.getParent() instanceof TypeLiteral
                        ? "a class literal"
                        : ancestor(st, MethodReference.class) != null
                            ? "a method reference"
                            : st.getParent() instanceof SingleVariableDeclaration v
                                    && v.getParent() instanceof CatchClause
                                ? "a catch"
                                : null;
        if (kind != null) {
          throw new Refusal(
              subName
                  + " is used in "
                  + kind
                  + " at "
                  + where
                  + ", which tells it from "
                  + baseName
                  + "; once it is a function nothing could.");
        }
        if (st.getParent() instanceof ClassInstanceCreation cic && cic.getType() == st) {
          es.add(
              new TextEdit(
                  cic.getStartPosition(),
                  Nodes.end(cic),
                  construction(text, cic, subConstructors, subText, baseName, subFn, where)));
        } else {
          es.add(new TextEdit(n.getStartPosition(), Nodes.end(n), baseName));
        }
      }
    }
    List<String> chosen = new ArrayList<>();
    chosen.add(baseType.getQualifiedName());
    for (Object o : subUnit.imports()) {
      ImportDeclaration imp = (ImportDeclaration) o;
      if (!imp.isStatic() && !imp.isOnDemand()) {
        chosen.add(imp.getName().getFullyQualifiedName());
      }
    }
    byFile.put(opened.file(), null);
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            holding.texts(),
            byFile,
            chosen,
            "turning " + subName + " into a function of " + baseName,
            "Nothing was changed.");
    Stubs.Declared declared = written.declared();
    return new Outcome(
        "turned "
            + subName
            + " into a function: "
            + baseName
            + " holds a "
            + fnType
            + " ("
            + field
            + "), "
            + baseName
            + "::"
            + subFn
            + " is "
            + subName
            + "'s "
            + method
            + " and "
            + baseName
            + "::"
            + ownFn
            + " its own; new "
            + subName
            + "(...) is new "
            + baseName
            + "(..., "
            + baseName
            + "::"
            + subFn
            + ") ("
            + project.relative(opened.file())
            + " deleted)"
            + written.said(),
        declared);
  }

  /** Refuses a body that uses the instance: a function has none. */
  private static void usesInstance(CompilationUnit unit, MethodDeclaration m, String what) {
    String use = null;
    ASTNode at = null;
    for (ASTNode n : Nodes.all(m.getBody(), ASTNode.class)) {
      if (use != null) {
        break;
      }
      if (n instanceof ThisExpression
          || n instanceof SuperMethodInvocation
          || n instanceof SuperFieldAccess) {
        use = n.toString();
        at = n;
      } else if (n instanceof SimpleName s && !s.isDeclaration()) {
        boolean bare =
            !(s.getParent() instanceof org.eclipse.jdt.core.dom.QualifiedName q && q.getName() == s)
                && !(s.getParent() instanceof org.eclipse.jdt.core.dom.FieldAccess f
                    && f.getName() == s)
                && !(s.getParent() instanceof MethodInvocation call
                    && call.getName() == s
                    && call.getExpression() != null);
        boolean member =
            (s.resolveBinding() instanceof IVariableBinding v
                    && v.isField()
                    && !Modifier.isStatic(v.getModifiers()))
                || (s.resolveBinding() instanceof IMethodBinding b
                    && !Modifier.isStatic(b.getModifiers())
                    && !b.isConstructor());
        if (bare && member) {
          use = s.getIdentifier();
          at = s;
        }
      }
    }
    if (use != null) {
      throw new Refusal(
          what
              + " uses "
              + use
              + " (line "
              + unit.getLineNumber(at.getStartPosition())
              + "), a member of the instance, and a function has no instance. Pass what it needs"
              + " as a parameter first (add_parameter).");
    }
  }

  /** The name of the one abstract method of {@code type}, as it resolves in the base's file. */
  private static String samOf(
      SourceProject project,
      Path file,
      String text,
      TypeDeclaration base,
      String type,
      MethodDeclaration own)
      throws Exception {
    ITypeBinding t = Jdt.probeType(project, file, text, base, type, List.of());
    IMethodBinding sam = t == null ? null : t.getFunctionalInterfaceMethod();
    if (sam != null && sam.getParameterTypes().length == own.parameters().size()) {
      return sam.getName();
    }
    throw new Refusal(
        type
            + " is not a functional interface whose method takes "
            + own.parameters().size()
            + " parameter(s), as "
            + own.getName()
            + " does. Name one that resolves in "
            + project.relative(file)
            + ", or leave type out for an interface the tool declares.");
  }

  /** {@code new Sub(args)} as {@code new Base(the super call's arguments, Base::fn)}. */
  private static String construction(
      String text,
      ClassInstanceCreation cic,
      List<MethodDeclaration> constructors,
      String subText,
      String baseName,
      String fn,
      String where) {
    if (cic.getAnonymousClassDeclaration() != null || cic.getExpression() != null) {
      throw new Refusal("the construction at " + where + " has a class body or an outer instance.");
    }
    List<String> out = new ArrayList<>();
    IMethodBinding called = cic.resolveConstructorBinding();
    MethodDeclaration ctor = null;
    for (MethodDeclaration c : constructors) {
      if (called != null
          && c.resolveBinding() != null
          && Bindings.same(c.resolveBinding(), called)) {
        ctor = c;
      }
    }
    if (ctor != null) {
      List<?> statements = ctor.getBody().statements();
      if (statements.size() > 1
          || (statements.size() == 1
              && !(statements.get(0) instanceof SuperConstructorInvocation))) {
        throw new Refusal(
            "a constructor of the subclass does more than call super(...), which the function"
                + " would not.");
      }
      List<?> superArgs =
          statements.isEmpty()
              ? List.of()
              : ((SuperConstructorInvocation) statements.get(0)).arguments();
      List<String> order = new ArrayList<>();
      List<String> params = new ArrayList<>();
      for (Object p : ctor.parameters()) {
        params.add(((SingleVariableDeclaration) p).getName().getIdentifier());
      }
      for (Object a : superArgs) {
        Expression arg = (Expression) a;
        List<TextEdit> swaps = new ArrayList<>();
        for (SimpleName n : Nodes.all(arg, SimpleName.class)) {
          int i = params.indexOf(n.getIdentifier());
          if (i >= 0 && n.resolveBinding() instanceof IVariableBinding v && v.isParameter()) {
            order.add(n.getIdentifier());
            Expression given = (Expression) cic.arguments().get(i);
            swaps.add(
                new TextEdit(
                    n.getStartPosition(),
                    Nodes.end(n),
                    n == arg ? src(text, given) : LoopToStream.primary(text, given)));
          }
        }
        out.add(TextEdit.apply(src(subText, arg), swaps, arg.getStartPosition()));
      }
      if (!order.equals(params)) {
        for (Object a : cic.arguments()) {
          if (!(a instanceof SimpleName
              || a instanceof NumberLiteral
              || a instanceof StringLiteral
              || a instanceof NullLiteral
              || a instanceof ThisExpression)) {
            throw new Refusal(
                "the construction at "
                    + where
                    + " passes "
                    + src(text, (ASTNode) a)
                    + ", and the subclass's constructor does not pass each parameter on once, in"
                    + " order; the argument would run another number of times or in another order."
                    + " Put it in a local first (extract_variable).");
          }
        }
      }
    }
    out.add(baseName + "::" + fn);
    return "new " + baseName + "(" + String.join(", ", out) + ")";
  }

  /** The source of the parameters of {@code m}, between its parentheses. */
  private static String paramsText(String text, MethodDeclaration m) {
    List<?> ps = m.parameters();
    return ps.isEmpty()
        ? ""
        : text.substring(
            ((ASTNode) ps.get(0)).getStartPosition(), Nodes.end((ASTNode) ps.get(ps.size() - 1)));
  }

  /** The offset of the parenthesis that closes the parameters of {@code m}. */
  private static int paramsEnd(String text, MethodDeclaration m) {
    List<?> ps = m.parameters();
    int from = ps.isEmpty() ? Nodes.end(m.getName()) : Nodes.end((ASTNode) ps.get(ps.size() - 1));
    return text.indexOf(')', from);
  }

  /** {@code " throws A, B"} as {@code m} declares it, or nothing. */
  private static String thrownText(String text, MethodDeclaration m) {
    List<?> ts = m.thrownExceptionTypes();
    return ts.isEmpty()
        ? ""
        : " throws "
            + text.substring(
                ((ASTNode) ts.get(0)).getStartPosition(),
                Nodes.end((ASTNode) ts.get(ts.size() - 1)));
  }

  /** Where the header of {@code m} starts, after its Javadoc. */
  private static int headerStart(String text, BodyDeclaration m) {
    if (m.getJavadoc() == null) {
      return m.getStartPosition();
    }
    int at = Nodes.end(m.getJavadoc());
    while (Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at;
  }

  private static <T extends ASTNode> T ancestor(ASTNode n, Class<T> type) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (type.isInstance(p)) {
        return type.cast(p);
      }
    }
    return null;
  }

  private static String src(String text, ASTNode n) {
    return text.substring(n.getStartPosition(), Nodes.end(n));
  }
}
