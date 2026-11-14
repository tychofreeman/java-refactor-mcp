package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.Pattern;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * add_parameter with type_parameter: a class gets a type parameter, and every use of the class in
 * the project passes one type argument for it, {@code value}, so each expression outside the class
 * has the type it had. Inside the class's own instance code a use passes the parameter itself; in
 * its static code, {@code value}.
 */
final class TypeParameters {

  private TypeParameters() {}

  /** Each primitive type's boxed type, which can be a type argument. */
  private static final Map<String, String> BOXED =
      Map.of(
          "int", "Integer",
          "long", "Long",
          "boolean", "Boolean",
          "double", "Double",
          "float", "Float",
          "char", "Character",
          "short", "Short",
          "byte", "Byte");

  static Outcome add(SourceProject project, Args args) throws Exception {
    String written = args.required("type_parameter").strip();
    String value = args.required("value").strip();
    Precondition.require(
        Precondition.that(
            () -> !args.has("parameter") && !args.has("type"),
            () ->
                "add_parameter with type_parameter adds a type parameter, which has no parameter"
                    + " or type of its own: leave them out. Its bound goes in type_parameter"
                    + " (\"T extends Comparable<T>\")."),
        Precondition.that(
            () -> args.has("name"),
            () ->
                "add_parameter with type_parameter needs name, the class that gets the type"
                    + " parameter."),
        Precondition.that(
            () -> !BOXED.containsKey(value),
            () ->
                value
                    + " is a primitive, which cannot be a type argument. Pass value "
                    + BOXED.get(value)
                    + "; where a member's type is "
                    + value
                    + ", change it to "
                    + BOXED.get(value)
                    + " first (change_type, which checks the uses a boxed type changes)."));
    Signatures.parsesAs("type_parameter", written, "class X<" + written + "> {}");
    Signatures.parsesAs("value", value, "class X { " + value + " x; }");
    String parameter =
        Nodes.all(Jdt.parseSyntax("class X<" + written + "> {}"), TypeParameter.class)
            .get(0)
            .getName()
            .getIdentifier();
    String rel = args.required("file");
    String name = args.required("name");
    Path file = project.javaFile(rel);
    if (namesMethod(file, name, rel)) {
      return toMethod(project, args, written, parameter, value);
    }
    String simple = name.substring(name.lastIndexOf('.') + 1);
    Holding holding = Holding.of(project, simple, file);
    CompilationUnit home = holding.units().get(file);
    if (home == null) {
      throw new Refusal(rel + " is not a Java file under a source root.");
    }
    Declarations.Named named = Declarations.find(home, name, rel);
    if (!(named.decl() instanceof AbstractTypeDeclaration type)) {
      throw new Refusal(
          named.label()
              + " is not a class or a method. add_parameter with type_parameter takes the name of"
              + " a class or a method of "
              + rel
              + ".");
    }
    return toClass(project, args, holding, file, type, named.label(), written, parameter, value);
  }

  /**
   * The type argument every use passes: its text, as any file can write it, and its qualified name,
   * or null when it does not resolve in the class's file.
   */
  private record Value(String text, String qualified) {}

  private static Outcome toClass(
      SourceProject project,
      Args args,
      Holding holding,
      Path file,
      AbstractTypeDeclaration type,
      String label,
      String written,
      String parameter,
      String value)
      throws Exception {
    ITypeBinding binding = type.resolveBinding();
    Precondition.require(
        Precondition.that(
            () -> binding != null,
            () -> label + " does not resolve; fix its compile errors first."),
        Precondition.that(
            () -> !(type instanceof EnumDeclaration),
            () ->
                label
                    + " is an enum, which cannot have type parameters. Add it to a class, or to"
                    + " one of its methods."),
        Precondition.that(
            () -> !(type instanceof AnnotationTypeDeclaration),
            () -> label + " is an annotation, which cannot have type parameters."),
        Precondition.that(
            () -> !(type instanceof RecordDeclaration) && !((TypeDeclaration) type).isInterface(),
            () ->
                label
                    + " is "
                    + (type instanceof RecordDeclaration ? "a record" : "an interface")
                    + "; add_parameter adds a type parameter to a class only, so far. Declare it"
                    + " with replace_declaration and change the uses in the same batch."),
        Precondition.that(
            () -> !throwable(binding),
            () ->
                label
                    + " is a Throwable, and a generic class cannot extend Throwable. Add the type"
                    + " parameter to a class it holds, or to a method."));
    TypeDeclaration target = (TypeDeclaration) type;
    for (SimpleName n : Nodes.all(target, SimpleName.class)) {
      if (n.getIdentifier().equals(parameter) && n.resolveBinding() instanceof ITypeBinding t) {
        int line = holding.units().get(file).getLineNumber(n.getStartPosition());
        throw new Refusal(
            t.isTypeVariable()
                ? label + " already has a type parameter " + parameter + ". Choose another name."
                : parameter
                    + " is a type that "
                    + label
                    + " already names (line "
                    + line
                    + "); as its type parameter, "
                    + parameter
                    + " would hide it. Choose another name.");
      }
    }
    List<?> declared = target.typeParameters();
    int at = declared.size();
    String before = args.string("before");
    if (before != null) {
      List<String> names = new ArrayList<>();
      for (Object o : declared) {
        names.add(((TypeParameter) o).getName().getIdentifier());
      }
      at = names.indexOf(before);
      if (at < 0) {
        throw new Refusal(
            label
                + " has no type parameter "
                + before
                + " to put "
                + parameter
                + " before"
                + (names.isEmpty() ? "." : ": it has " + String.join(", ", names) + "."));
      }
    }
    String text = holding.texts().get(file);
    List<String> chosen = args.list("imports");
    Value v = value(project, file, text, target, value, chosen);
    String qualified = binding.getErasure().getQualifiedName();
    String what = "adding the type parameter " + parameter + " to " + label;

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    edits.computeIfAbsent(file, k -> new ArrayList<>()).add(declaration(target, at, written));
    List<String> members = args.list("members");
    edits
        .get(file)
        .addAll(
            typed(
                holding.units().get(file),
                target,
                label,
                members,
                v,
                parameter,
                args.required("file")));
    Set<String> files = new TreeSet<>();
    List<String> casts = new ArrayList<>();
    int outside = 0;
    int inside = 0;
    int raw = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      CompilationUnit unit = e.getValue();
      String in = project.relative(e.getKey()).toString();
      List<TextEdit> list = edits.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
      for (Type t : Nodes.all(unit, Type.class)) {
        if (!named(t)) {
          continue;
        }
        ITypeBinding b = t.resolveBinding();
        if (b == null || b.isRecovered()) {
          if (lastName(t).equals(target.getName().getIdentifier())) {
            throw new Refusal(
                lastName(t)
                    + " at line "
                    + unit.getLineNumber(t.getStartPosition())
                    + " of "
                    + in
                    + " does not resolve, so whether it is "
                    + label
                    + " is not known; fix its compile errors first.");
          }
          continue;
        }
        if (b.isTypeVariable() || !qualified.equals(b.getErasure().getQualifiedName())) {
          continue;
        }
        boolean own = inInstanceCode(t, qualified);
        String argument = own ? parameter : v.text();
        if (t.getParent() instanceof ParameterizedType p && p.getType() == t) {
          if (p.typeArguments().isEmpty()) {
            continue; // a diamond infers it
          }
          list.add(SignatureChange.insertAt(p.typeArguments(), at, argument));
        } else if (!declared.isEmpty()) {
          raw += exempt(t) ? 0 : 1;
          continue; // a raw use stays raw
        } else if (exempt(t)) {
          continue;
        } else if (t.getParent() instanceof ClassInstanceCreation c
            && c.getType() == t
            && c.getAnonymousClassDeclaration() == null
            && fixes(c, qualified)) {
          list.add(new TextEdit(Nodes.end(t), Nodes.end(t), "<>"));
        } else {
          list.add(new TextEdit(Nodes.end(t), Nodes.end(t), "<" + argument + ">"));
          if (t.getParent() instanceof CastExpression) {
            casts.add("line " + unit.getLineNumber(t.getStartPosition()) + " of " + in);
          }
        }
        if (own) {
          inside++;
        } else {
          outside++;
          files.add(in);
        }
      }
    }

    CallTargets targets = CallTargets.of(holding.units().values());
    String fix =
        "Change the uses that do not compile with "
            + target.getName().getIdentifier()
            + "<"
            + v.text()
            + "> first, or choose another value."
            + (members.isEmpty()
                ? ""
                : " Where a member's body needs a "
                    + v.text()
                    + ", give "
                    + parameter
                    + " a bound that has what it uses (type_parameter \""
                    + parameter
                    + " extends ...\"), or leave that member out of members.");
    Edits.WrittenFiles gated = Edits.gatedFiles(project, holding.texts(), edits, chosen, what, fix);
    Map<Path, String> texts = gated.texts();
    Stubs.Declared stubs = gated.declared();
    List<Path> parsed = new ArrayList<>(holding.units().keySet());
    Map<Path, CompilationUnit> after = Jdt.parseAll(project, parsed);
    Map<Path, String> full = fullDiamonds(after, texts, qualified, at, parameter, v.text());
    if (!full.isEmpty()) {
      Gate.applyOrRefuse(project, full, what, fix);
      after = Jdt.parseAll(project, parsed);
    }
    requireNoRawUse(project, after.values(), qualified, raw, what, label);
    targets.require(project, after.values(), what);

    return new Outcome(
        "added the type parameter "
            + parameter
            + " to "
            + label
            + (members.isEmpty()
                ? ""
                : "; " + v.text() + " became " + parameter + " in " + Extract.and(members))
            + "; "
            + outside
            + (outside == 1 ? " use passes " : " uses pass ")
            + v.text()
            + (files.isEmpty()
                ? ""
                : " in "
                    + files.size()
                    + (files.size() == 1 ? " file (" : " files (")
                    + String.join(", ", files)
                    + ")")
            + (inside == 0
                ? ""
                : "; "
                    + inside
                    + (inside == 1 ? " use" : " uses")
                    + " in its own instance code "
                    + (inside == 1 ? "passes " : "pass ")
                    + parameter)
            + (casts.isEmpty()
                ? ""
                : "; a cast to it is now unchecked at " + String.join(", ", casts))
            + (hasParamTags(target) ? "; its Javadoc has no @param <" + parameter + "> yet" : "")
            + gated.said(),
        stubs);
  }

  /** Whether {@code t} names a type by a name, as a use of a class does; {@code var} does not. */
  private static boolean named(Type t) {
    return !t.isVar()
        && (t instanceof SimpleType
            || t instanceof NameQualifiedType
            || t instanceof QualifiedType);
  }

  private static String lastName(Type t) {
    return switch (t) {
      case SimpleType s ->
          s.getName()
              .getFullyQualifiedName()
              .substring(s.getName().getFullyQualifiedName().lastIndexOf('.') + 1);
      case NameQualifiedType n -> n.getName().getIdentifier();
      case QualifiedType q -> q.getName().getIdentifier();
      default -> "";
    };
  }

  private static boolean throwable(ITypeBinding binding) {
    for (ITypeBinding t = binding; t != null; t = t.getSuperclass()) {
      if (t.getErasure().getQualifiedName().equals("java.lang.Throwable")) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasParamTags(TypeDeclaration target) {
    if (target.getJavadoc() == null) {
      return false;
    }
    for (Object o : target.getJavadoc().tags()) {
      if (TagElement.TAG_PARAM.equals(((TagElement) o).getTagName())) {
        return true;
      }
    }
    return false;
  }

  /** The edit that declares {@code written} at {@code at} of {@code target}'s type parameters. */
  private static TextEdit declaration(TypeDeclaration target, int at, String written) {
    if (target.typeParameters().isEmpty()) {
      int end = Nodes.end(target.getName());
      return new TextEdit(end, end, "<" + written + ">");
    }
    return SignatureChange.insertAt(target.typeParameters(), at, written);
  }

  /**
   * {@code value} as every file can write it: resolved once, as a field of {@code target} in its
   * file would be, and named by its simple names with a member type's outer types; as written when
   * it does not resolve there, which the gate then judges in each file.
   */
  private static Value value(
      SourceProject project,
      Path file,
      String text,
      TypeDeclaration target,
      String value,
      List<String> chosen)
      throws IOException {
    int open = text.indexOf('{', headerEnd(target)) + 1;
    String probe =
        text.substring(0, open) + " " + value + " jrefactorProbe; " + text.substring(open);
    String fixed = Imports.fix(project, file, text, probe, chosen).text();
    for (VariableDeclarationFragment f :
        Nodes.all(Jdt.parse(project, file, fixed), VariableDeclarationFragment.class)) {
      IVariableBinding b = f.resolveBinding();
      if (f.getName().getIdentifier().equals("jrefactorProbe")
          && b != null
          && b.getType() != null
          && !b.getType().isRecovered()) {
        ITypeBinding t = b.getType();
        if (t.isTypeVariable()) {
          throw new Refusal(
              value
                  + " is a type parameter of "
                  + target.getName().getIdentifier()
                  + ", which a use outside it cannot name. Pass a type every use can name.");
        }
        return new Value(Extract.typeName(t), t.getQualifiedName());
      }
    }
    return new Value(value, null);
  }

  /** The offset past the last part of {@code target}'s header before its body. */
  private static int headerEnd(TypeDeclaration target) {
    List<ASTNode> parts = new ArrayList<>();
    for (List<?> list :
        List.of(target.typeParameters(), target.superInterfaceTypes(), target.permittedTypes())) {
      for (Object o : list) {
        parts.add((ASTNode) o);
      }
    }
    if (target.getSuperclassType() != null) {
      parts.add(target.getSuperclassType());
    }
    int end = Nodes.end(target.getName());
    for (ASTNode part : parts) {
      end = Math.max(end, Nodes.end(part));
    }
    return end;
  }

  /**
   * Whether {@code node} is in the instance code of the class named {@code qualified}, where its
   * type parameters can be named: in its header or a member that is not static, with no static
   * member or nested type between.
   */
  private static boolean inInstanceCode(ASTNode node, String qualified) {
    for (ASTNode n = node.getParent(); n != null; n = n.getParent()) {
      if (n instanceof AbstractTypeDeclaration d) {
        ITypeBinding b = d.resolveBinding();
        if (b != null && qualified.equals(b.getErasure().getQualifiedName())) {
          return true;
        }
        boolean inInterface = d.getParent() instanceof TypeDeclaration p && p.isInterface();
        if (Modifier.isStatic(d.getModifiers())
            || inInterface
            || !(d instanceof TypeDeclaration c)
            || c.isInterface()) {
          return false;
        }
      } else if (n instanceof BodyDeclaration d && Modifier.isStatic(d.getModifiers())) {
        return false;
      }
    }
    return false;
  }

  /**
   * Whether {@code t} is a use that cannot take a type argument: the type of an instanceof or of a
   * pattern, a class literal, a method reference's type, or an array creation's element type.
   */
  private static boolean exempt(Type t) {
    ASTNode n = t;
    while (n.getParent() instanceof ArrayType) {
      n = n.getParent();
    }
    ASTNode p = n.getParent();
    return p instanceof InstanceofExpression
        || p instanceof TypeLiteral
        || p instanceof ArrayCreation
        || p instanceof MethodReference
        || (p instanceof SingleVariableDeclaration && p.getParent() instanceof Pattern);
  }

  /**
   * Whether a declared type fixes what {@code c} creates, so that a diamond infers it: {@code c}
   * initializes a variable declared with the class's type (not var), is assigned to one, or is
   * returned from a method that returns it.
   */
  private static boolean fixes(ClassInstanceCreation c, String qualified) {
    ASTNode x = c;
    while (x.getParent() instanceof ParenthesizedExpression) {
      x = x.getParent();
    }
    ASTNode p = x.getParent();
    ITypeBinding target = null;
    if (p instanceof VariableDeclarationFragment f && f.getInitializer() == x) {
      Type declared =
          switch (f.getParent()) {
            case VariableDeclarationStatement s -> s.getType();
            case FieldDeclaration d -> d.getType();
            case VariableDeclarationExpression e -> e.getType();
            default -> null;
          };
      if (declared == null || declared.isVar() || f.getExtraDimensions() > 0) {
        return false;
      }
      target = declared.resolveBinding();
    } else if (p instanceof Assignment a
        && a.getRightHandSide() == x
        && a.getOperator() == Assignment.Operator.ASSIGN) {
      target = a.getLeftHandSide().resolveTypeBinding();
    } else if (p instanceof ReturnStatement) {
      for (ASTNode n = p.getParent(); n != null; n = n.getParent()) {
        if (n instanceof LambdaExpression) {
          return false;
        }
        if (n instanceof MethodDeclaration m) {
          target = m.getReturnType2() == null ? null : m.getReturnType2().resolveBinding();
          break;
        }
      }
    }
    return target != null
        && !target.isArray()
        && !target.isTypeVariable()
        && qualified.equals(target.getErasure().getQualifiedName());
  }

  /**
   * The files of {@code texts} in which a diamond of the class, as {@code after} parses it, infers
   * another type argument at {@code at} than its place asks for, with that diamond written out in
   * full; empty when every diamond infers what it should.
   */
  private static Map<Path, String> fullDiamonds(
      Map<Path, CompilationUnit> after,
      Map<Path, String> texts,
      String qualified,
      int at,
      String parameter,
      String value) {
    Map<Path, String> out = new LinkedHashMap<>();
    for (Map.Entry<Path, CompilationUnit> e : after.entrySet()) {
      List<TextEdit> edits = new ArrayList<>();
      for (ClassInstanceCreation c : Nodes.all(e.getValue(), ClassInstanceCreation.class)) {
        ITypeBinding b = c.resolveTypeBinding();
        if (!(c.getType() instanceof ParameterizedType p)
            || !p.typeArguments().isEmpty()
            || b == null
            || b.getTypeArguments().length <= at
            || !qualified.equals(b.getErasure().getQualifiedName())) {
          continue;
        }
        String expected = inInstanceCode(c.getType(), qualified) ? parameter : value;
        List<String> arguments = new ArrayList<>();
        for (ITypeBinding argument : b.getTypeArguments()) {
          arguments.add(Extract.typeName(argument));
        }
        if (!arguments.get(at).equals(expected)) {
          arguments.set(at, expected);
          edits.add(
              new TextEdit(
                  Nodes.end(p.getType()), Nodes.end(p), "<" + String.join(", ", arguments) + ">"));
        }
      }
      if (!edits.isEmpty() && texts.containsKey(e.getKey())) {
        out.put(e.getKey(), TextEdit.apply(texts.get(e.getKey()), edits, 0));
      }
    }
    return out;
  }

  /**
   * Refuses when the written files hold more raw uses of the class than the {@code before} it had:
   * a use the walk did not give its type argument, which compiles with a warning only.
   */
  private static void requireNoRawUse(
      SourceProject project,
      Collection<CompilationUnit> after,
      String qualified,
      int before,
      String what,
      String label) {
    List<String> raw = new ArrayList<>();
    for (CompilationUnit unit : after) {
      for (Type t : Nodes.all(unit, Type.class)) {
        ITypeBinding b = t.resolveBinding();
        if (named(t)
            && !(t.getParent() instanceof ParameterizedType)
            && b != null
            && b.isRawType()
            && qualified.equals(b.getErasure().getQualifiedName())
            && !exempt(t)) {
          raw.add(
              "line "
                  + unit.getLineNumber(t.getStartPosition())
                  + " of "
                  + project.relative(Jdt.fileOf(unit)));
        }
      }
    }
    if (raw.size() > before) {
      throw new Refusal(
          what
              + " would leave "
              + label
              + " raw at "
              + String.join(", ", raw)
              + ": a use add_parameter does not give its type argument yet. Declare the type"
              + " parameter with replace_declaration and change the uses in the same batch.");
    }
  }

  /**
   * The edits that make {@code v} the type parameter in the declared types of {@code members} of
   * {@code target}: a field's type, a method's or constructor's parameter and return types, with
   * the type arguments in them. Bodies are left to the gate.
   */
  private static List<TextEdit> typed(
      CompilationUnit unit,
      TypeDeclaration target,
      String label,
      List<String> members,
      Value v,
      String parameter,
      String rel) {
    List<TextEdit> edits = new ArrayList<>();
    if (!members.isEmpty() && v.qualified() == null) {
      throw new Refusal(
          v.text()
              + " does not resolve in "
              + label
              + "'s file, so where it stands in members cannot be found. Pass a type that file can"
              + " name, or import it first.");
    }
    for (String member : members) {
      BodyDeclaration d = member(unit, target, label, member, rel);
      String named = label + "." + member;
      if (Modifier.isStatic(d.getModifiers())) {
        throw new Refusal(
            named
                + " is static, and a static member cannot use "
                + parameter
                + ", the type parameter of its class. Leave it out of members; a static method can"
                + " take a type parameter of its own (add_parameter with type_parameter and name "
                + named
                + ").");
      }
      List<Type> declared = new ArrayList<>();
      if (d instanceof FieldDeclaration f) {
        declared.add(f.getType());
        for (Object o : f.fragments()) {
          Expression init = ((VariableDeclarationFragment) o).getInitializer();
          for (ArrayCreation a :
              init == null ? List.<ArrayCreation>of() : Nodes.all(init, ArrayCreation.class)) {
            ITypeBinding e = a.getType().getElementType().resolveBinding();
            if (e != null && v.qualified().equals(e.getQualifiedName())) {
              throw new Refusal(
                  named
                      + " is created as "
                      + a
                      + ", and an array of "
                      + parameter
                      + " cannot be created. Leave "
                      + member
                      + " out of members, or make it a List first.");
            }
          }
        }
      } else if (d instanceof MethodDeclaration m) {
        IMethodBinding mb = m.resolveBinding();
        List<IMethodBinding> overridden = mb == null ? List.of() : Hierarchy.overridden(mb);
        if (!overridden.isEmpty()) {
          IMethodBinding over = overridden.get(0);
          throw new Refusal(
              named
                  + " overrides "
                  + over.getDeclaringClass().getErasure().getName()
                  + "."
                  + over.getName()
                  + ", whose signature keeps "
                  + v.text()
                  + ": with "
                  + parameter
                  + " in its place it would no longer override it. Leave "
                  + member
                  + " out of members.");
        }
        if (m.getReturnType2() != null) {
          declared.add(m.getReturnType2());
        }
        for (Object o : m.parameters()) {
          declared.add(((SingleVariableDeclaration) o).getType());
        }
      } else {
        throw new Refusal(
            named + " is not a field, a method or a constructor. Name those in members.");
      }
      int before = edits.size();
      retype(declared, edits, v, parameter);
      if (edits.size() == before) {
        throw new Refusal(
            named
                + " has no "
                + v.text()
                + " in its declared types, so nothing in it would become "
                + parameter
                + ". Leave it out of members.");
      }
    }
    return edits;
  }

  /**
   * Adds to {@code edits} one that writes {@code parameter} for each type in {@code declared} that
   * is {@code v}, the type arguments in them included, outermost first.
   */
  private static void retype(List<Type> declared, List<TextEdit> edits, Value v, String parameter) {
    for (Type root : declared) {
      for (Type t : Nodes.all(root, Type.class)) {
        ITypeBinding b = t.resolveBinding();
        boolean head = t.getParent() instanceof ParameterizedType p && p.getType() == t;
        boolean within = false;
        for (TextEdit e : edits) {
          within |= e.start() <= t.getStartPosition() && Nodes.end(t) <= e.end();
        }
        if (!head && !within && b != null && v.qualified().equals(b.getQualifiedName())) {
          edits.add(new TextEdit(t.getStartPosition(), Nodes.end(t), parameter));
        }
      }
    }
  }

  /**
   * The member of {@code target} that {@code member} names, as replace_declaration finds one; a
   * field declared with others in one statement is refused.
   */
  private static BodyDeclaration member(
      CompilationUnit unit, TypeDeclaration target, String label, String member, String rel) {
    String base = member.contains("(") ? member.substring(0, member.indexOf('(')).strip() : member;
    for (FieldDeclaration f : target.getFields()) {
      for (Object o : f.fragments()) {
        if (f.fragments().size() > 1
            && ((VariableDeclarationFragment) o).getName().getIdentifier().equals(base)) {
          throw new Refusal(
              label
                  + "."
                  + base
                  + " is declared with other fields in one statement. Split it into one"
                  + " declaration per field first.");
        }
      }
    }
    return Declarations.find(unit, label + "." + member, rel).decl();
  }

  /**
   * add_parameter with type_parameter on a method: it and every declaration of its family declare
   * the type parameter, and each {@code value} in their parameter and return types becomes it.
   * Calls are not edited: each must infer {@code value} for it and keep the types it had.
   */
  private static Outcome toMethod(
      SourceProject project, Args args, String written, String parameter, String value)
      throws Exception {
    MethodFamily family =
        MethodFamily.of(project, args, true, "so its type parameters cannot change.");
    MethodDeclaration target = family.target;
    String label = family.label;
    Precondition.require(
        Precondition.that(
            () -> !args.has("members"),
            () ->
                "members names the members of a class that get its type parameter; for a method,"
                    + " every "
                    + value
                    + " in its parameter and return types becomes "
                    + parameter
                    + ". Leave members out."),
        Precondition.that(
            () -> !target.isConstructor(),
            () ->
                label
                    + " is a constructor. Add the type parameter to its class (name "
                    + target.getName().getIdentifier()
                    + ")."),
        Precondition.that(
            () -> target.getParent() instanceof TypeDeclaration,
            () ->
                label
                    + " is declared in a record, an enum or an anonymous class; add_parameter adds a"
                    + " type parameter to a method of a class or an interface only, so far."),
        Precondition.that(
            () -> family.lambdas.isEmpty(),
            () ->
                label
                    + " is implemented by a lambda ("
                    + (family.lambdas.isEmpty()
                        ? ""
                        : family.lambdas.get(0).file().rel()
                            + ", line "
                            + family.lambdas.get(0).file().line(family.lambdas.get(0).node()))
                    + "), and a lambda cannot implement a generic method. Turn the lambda into an"
                    + " anonymous class first (convert_lambda with to anonymous)."));
    for (Signatures.Decl d : family.decls) {
      for (SimpleName n : Nodes.all(d.m(), SimpleName.class)) {
        if (n.getIdentifier().equals(parameter) && n.resolveBinding() instanceof ITypeBinding t) {
          throw new Refusal(
              t.isTypeVariable()
                  ? Names.methodLabel(d.binding())
                      + " already names a type parameter "
                      + parameter
                      + " (line "
                      + d.file().line(n)
                      + " of "
                      + d.file().rel()
                      + "). Choose another name."
                  : parameter
                      + " is a type that "
                      + Names.methodLabel(d.binding())
                      + " already names (line "
                      + d.file().line(n)
                      + " of "
                      + d.file().rel()
                      + "); as its type parameter, "
                      + parameter
                      + " would hide it. Choose another name.");
        }
      }
    }
    Signatures.Source home = family.sources.get(project.javaFile(args.required("file")));
    TypeDeclaration owner = (TypeDeclaration) target.getParent();
    List<String> chosen = args.list("imports");
    Value v = value(project, home.file(), home.text(), owner, value, chosen);
    if (v.qualified() == null) {
      throw new Refusal(
          value
              + " does not resolve in "
              + home.rel()
              + ", so where it stands in "
              + label
              + " cannot be found. Pass a type that file can name, or import it first.");
    }
    List<TextEdit> inParameters = new ArrayList<>();
    for (Object o : target.parameters()) {
      retype(List.of(((SingleVariableDeclaration) o).getType()), inParameters, v, parameter);
    }
    List<TextEdit> inReturn = new ArrayList<>();
    if (target.getReturnType2() != null) {
      retype(List.of(target.getReturnType2()), inReturn, v, parameter);
    }
    if (inParameters.isEmpty()) {
      throw new Refusal(
          inReturn.isEmpty()
              ? label
                  + " has no "
                  + v.text()
                  + " in its parameter or return types, so nothing in it would become "
                  + parameter
                  + ". Name a method whose parameters use it."
              : label
                  + " has "
                  + v.text()
                  + " in its return type only: "
                  + parameter
                  + " would be in the return type only, so no call could infer it from its"
                  + " arguments. Name a method whose parameters use it.");
    }
    String what = "adding the type parameter " + parameter + " to " + label;

    Map<Path, Map<String, List<String>>> before = callTypes(family);
    int index = target.typeParameters().size();

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    for (Signatures.Decl d : family.decls) {
      List<TextEdit> list = edits.computeIfAbsent(d.file().file(), k -> new ArrayList<>());
      MethodDeclaration m = d.m();
      List<?> declared = m.typeParameters();
      int at = declared.size();
      String beforeName = args.string("before");
      if (beforeName != null) {
        List<String> names = new ArrayList<>();
        for (Object o : declared) {
          names.add(((TypeParameter) o).getName().getIdentifier());
        }
        at = names.indexOf(beforeName);
        if (m == target) {
          index = at;
        }
        if (at < 0) {
          throw new Refusal(
              Names.methodLabel(d.binding())
                  + " has no type parameter "
                  + beforeName
                  + " to put "
                  + parameter
                  + " before"
                  + (names.isEmpty() ? "." : ": it has " + String.join(", ", names) + "."));
        }
      }
      if (declared.isEmpty()) {
        int start = m.getReturnType2().getStartPosition();
        list.add(new TextEdit(start, start, "<" + written + "> "));
      } else {
        list.add(SignatureChange.insertAt(declared, at, written));
      }
      List<Type> roots = new ArrayList<>();
      roots.add(m.getReturnType2());
      for (Object o : m.parameters()) {
        roots.add(((SingleVariableDeclaration) o).getType());
      }
      retype(roots, list, v, parameter);
    }
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Map.Entry<Path, Signatures.Source> e : family.sources.entrySet()) {
      texts.put(e.getKey(), e.getValue().text());
    }
    List<CompilationUnit> units = new ArrayList<>();
    for (Signatures.Source s : family.sources.values()) {
      units.add(s.unit());
    }
    CallTargets targets = CallTargets.of(units);
    String fix =
        "Where a body needs a "
            + v.text()
            + ", give "
            + parameter
            + " a bound that has what it uses (type_parameter \""
            + parameter
            + " extends ...\"), or choose another value.";
    Edits.WrittenFiles gated = Edits.gatedFiles(project, texts, edits, chosen, what, fix);
    Stubs.Declared stubs = gated.declared();
    Map<Path, CompilationUnit> after =
        Jdt.parseAll(project, new ArrayList<>(family.sources.keySet()));
    int calls = 0;
    int references = 0;
    for (Map.Entry<Path, Map<String, List<String>>> e : before.entrySet()) {
      CompilationUnit unit = after.get(e.getKey());
      for (Map.Entry<String, List<String>> c : e.getValue().entrySet()) {
        ASTNode n = byOrdinal(unit, c.getKey());
        IMethodBinding b = n == null ? null : Calls.binding(n);
        String was = c.getValue().get(0);
        String now = b == null ? "nothing" : types(b);
        if (!was.equals(now)) {
          throw new Refusal(
              what
                  + " would change the types of the call at line "
                  + c.getValue().get(1)
                  + " of "
                  + project.relative(e.getKey())
                  + ": it would infer "
                  + parameter
                  + " as "
                  + inferred(b, index)
                  + ", not "
                  + v.text()
                  + ". Give it an argument or a target typed with "
                  + v.text()
                  + " first, or leave the method as it is.");
        }
        if (n instanceof MethodReference) {
          references++;
        } else {
          calls++;
        }
      }
    }
    targets.require(project, after.values(), what);
    int others = family.decls.size() - 1;
    return new Outcome(
        "added the type parameter "
            + parameter
            + " to "
            + label
            + (others == 0
                ? ""
                : " and the "
                    + others
                    + (others == 1 ? " declaration" : " declarations")
                    + " it overrides or that override it")
            + "; "
            + v.text()
            + " became "
            + parameter
            + " in "
            + (inReturn.isEmpty() ? "its parameter types" : "its parameter and return types")
            + "; "
            + calls
            + (calls == 1 ? " call" : " calls")
            + (references == 0
                ? ""
                : " and "
                    + references
                    + (references == 1 ? " method reference" : " method references"))
            + (calls + references == 1 ? " infers " : " infer ")
            + v.text()
            + ", none changed"
            + gated.said(),
        stubs);
  }

  /**
   * The types of each call and method reference of {@code family}'s methods ({@link #types}), with
   * its line, by its file and its kind and number in that file ({@link #ordinal}).
   */
  private static Map<Path, Map<String, List<String>>> callTypes(MethodFamily family) {
    Map<Path, Map<String, List<String>>> before = new LinkedHashMap<>();
    Map<ASTNode, Signatures.Source> uses = new LinkedHashMap<>();
    for (Signatures.Call c : family.calls) {
      uses.put(c.node(), c.file());
    }
    Set<String> keys = new HashSet<>();
    for (Signatures.Decl d : family.decls) {
      keys.add(Signatures.key(d.binding()));
    }
    for (Signatures.Source s : family.sources.values()) {
      for (MethodReference r : Nodes.all(s.unit(), MethodReference.class)) {
        IMethodBinding b = r.resolveMethodBinding();
        if (b != null && keys.contains(Signatures.key(b.getMethodDeclaration()))) {
          uses.put(r, s);
        }
      }
    }
    for (Map.Entry<ASTNode, Signatures.Source> u : uses.entrySet()) {
      IMethodBinding b = Calls.binding(u.getKey());
      if (b != null) {
        before
            .computeIfAbsent(u.getValue().file(), k -> new LinkedHashMap<>())
            .put(
                ordinal(u.getKey()),
                List.of(types(b), Integer.toString(u.getValue().line(u.getKey()))));
      }
    }
    return before;
  }

  /** The return and parameter types of {@code b}, by their qualified names. */
  private static String types(IMethodBinding b) {
    List<String> out = new ArrayList<>();
    out.add(b.getReturnType().getQualifiedName());
    for (ITypeBinding t : b.getParameterTypes()) {
      out.add(t.getQualifiedName());
    }
    return String.join(" ", out);
  }

  /** Where {@code n} stands in its file: its kind and its number among the nodes of that kind. */
  private static String ordinal(ASTNode n) {
    return n.getClass().getSimpleName() + " " + Nodes.all(n.getRoot(), n.getClass()).indexOf(n);
  }

  /** The node of {@code unit} that {@link #ordinal} names; null when there is none. */
  private static ASTNode byOrdinal(CompilationUnit unit, String ordinal) {
    String kind = ordinal.substring(0, ordinal.indexOf(' '));
    int k = Integer.parseInt(ordinal.substring(ordinal.indexOf(' ') + 1));
    for (ASTNode n : Nodes.all(unit, ASTNode.class)) {
      if (n.getClass().getSimpleName().equals(kind) && k-- == 0) {
        return n;
      }
    }
    return null;
  }

  /** The type a call infers for the type parameter at {@code index}, as the result names it. */
  private static String inferred(IMethodBinding b, int index) {
    if (b != null && b.getTypeArguments().length > index) {
      return Extract.typeName(b.getTypeArguments()[index]);
    }
    return "another type";
  }

  /**
   * change_type with type_parameter: the bound of a type parameter of a class or a method becomes
   * {@code type} ({@code Object} takes it away), in every declaration of a method's family. Uses
   * are not edited: the gate judges them, every call keeps its target and every override stays one
   * ({@link CallTargets}), a method's calls keep their types, and a cast to the parameter that now
   * checks for another type is reported through {@link Exceptional}.
   */
  static Outcome bound(SourceProject project, Args args) throws Exception {
    String name = args.required("type_parameter").strip();
    String type = args.required("type").strip();
    Precondition.require(
        Precondition.that(
            () -> !args.has("parameter") && !args.flag("return_type"),
            () ->
                "change_type with type_parameter changes a bound, which has no parameter or"
                    + " return type: leave them out."),
        Precondition.that(
            () -> args.has("name"),
            () ->
                "change_type with type_parameter needs name, the class or method that declares "
                    + name
                    + "."));
    Signatures.parsesAs("type", type, "class X<T extends " + type + "> {}");
    String rel = args.required("file");
    Path file = project.javaFile(rel);
    String label;
    Map<Path, String> sources = new LinkedHashMap<>();
    Map<Path, CompilationUnit> units = new LinkedHashMap<>();
    Map<Path, List<TypeParameter>> declared = new LinkedHashMap<>();
    Map<Path, Map<String, List<String>>> calls = null;
    int declarations = 1;
    if (namesMethod(file, args.required("name"), rel)) {
      MethodFamily family =
          MethodFamily.of(project, args, true, "so its type parameters cannot change.");
      label = family.label;
      int index = typeParameter(family.target.typeParameters(), name, label);
      for (Signatures.Source s : family.sources.values()) {
        sources.put(s.file(), s.text());
        units.put(s.file(), s.unit());
      }
      for (Signatures.Decl d : family.decls) {
        declared
            .computeIfAbsent(d.file().file(), k -> new ArrayList<>())
            .add((TypeParameter) d.m().typeParameters().get(index));
      }
      calls = callTypes(family);
      declarations = family.decls.size();
    } else {
      String simple = args.required("name").substring(args.required("name").lastIndexOf('.') + 1);
      Holding holding = Holding.of(project, simple, file);
      CompilationUnit home = holding.units().get(file);
      if (home == null) {
        throw new Refusal(rel + " is not a Java file under a source root.");
      }
      Declarations.Named named = Declarations.find(home, args.required("name"), rel);
      label = named.label();
      List<?> list =
          switch (named.decl()) {
            case TypeDeclaration t -> t.typeParameters();
            case RecordDeclaration r -> r.typeParameters();
            default ->
                throw new Refusal(
                    label
                        + " is not a class, an interface, a record or a method, so it has no type"
                        + " parameters.");
          };
      int index = typeParameter(list, name, label);
      sources.putAll(holding.texts());
      units.putAll(holding.units());
      declared.computeIfAbsent(file, k -> new ArrayList<>()).add((TypeParameter) list.get(index));
    }
    boolean none = type.equals("Object") || type.equals("java.lang.Object");
    String what = "changing the bound of " + name + " of " + label + " to " + type;

    // The casts to the type parameter, by file and line, and its erasure before.
    Set<String> keys = new HashSet<>();
    String erasure = null;
    for (List<TypeParameter> list : declared.values()) {
      for (TypeParameter tp : list) {
        ITypeBinding b = tp.resolveBinding();
        if (b != null) {
          keys.add(b.getKey());
          erasure = b.getErasure().getQualifiedName();
        }
      }
    }
    List<String> casts = new ArrayList<>();
    for (Map.Entry<Path, CompilationUnit> e : units.entrySet()) {
      for (CastExpression c : Nodes.all(e.getValue(), CastExpression.class)) {
        ITypeBinding b = c.getType().resolveBinding();
        ITypeBinding element = b == null ? null : b.isArray() ? b.getElementType() : b;
        if (element != null && keys.contains(element.getKey())) {
          casts.add(
              "line "
                  + e.getValue().getLineNumber(c.getStartPosition())
                  + " of "
                  + project.relative(e.getKey()));
        }
      }
    }

    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    String old = "";
    for (Map.Entry<Path, List<TypeParameter>> e : declared.entrySet()) {
      String text = sources.get(e.getKey());
      List<TextEdit> list = new ArrayList<>();
      for (TypeParameter tp : e.getValue()) {
        int from = Nodes.end(tp.getName());
        old = text.substring(from, Nodes.end(tp)).strip();
        list.add(new TextEdit(from, Nodes.end(tp), none ? "" : " extends " + type));
      }
      edits.put(e.getKey(), list);
    }
    CallTargets targets = CallTargets.of(units.values());
    Edits.WrittenFiles gated =
        Edits.gatedFiles(
            project,
            sources,
            edits,
            args.list("imports"),
            what,
            "Change that use first, or choose a bound it meets.");
    Stubs.Declared stubs = gated.declared();
    Map<Path, CompilationUnit> after = Jdt.parseAll(project, new ArrayList<>(units.keySet()));
    int kept = 0;
    if (calls != null) {
      for (Map.Entry<Path, Map<String, List<String>>> e : calls.entrySet()) {
        for (Map.Entry<String, List<String>> c : e.getValue().entrySet()) {
          ASTNode n = byOrdinal(after.get(e.getKey()), c.getKey());
          IMethodBinding b = n == null ? null : Calls.binding(n);
          String now = b == null ? "nothing" : types(b);
          if (!c.getValue().get(0).equals(now)) {
            throw new Refusal(
                what
                    + " would change the types of the call at line "
                    + c.getValue().get(1)
                    + " of "
                    + project.relative(e.getKey())
                    + " (from "
                    + c.getValue().get(0)
                    + " to "
                    + now
                    + "). Change that call first, or choose a bound it meets.");
          }
          kept++;
        }
      }
    }
    targets.require(
        project,
        after.values(),
        what,
        "Give its class's extends its type argument first, or write the bound as \"Object & "
            + type
            + "\", which keeps "
            + name
            + "'s erasure.");
    String now = erasureAfter(after, declared, name);
    if (erasure != null && now != null && !erasure.equals(now)) {
      for (String at : casts) {
        Exceptional.change(
            project,
            "the cast to "
                + name
                + " at "
                + at
                + " now checks for "
                + simpleName(now)
                + ", where it "
                + (erasure.equals("java.lang.Object")
                    ? "checked nothing"
                    : "checked for " + simpleName(erasure))
                + ": it throws ClassCastException for a value that is not one",
            "write the bound as \"Object & " + type + "\", which keeps " + name + "'s erasure.");
      }
    }
    return new Outcome(
        (none ? "removed the bound of " : "changed the bound of ")
            + name
            + " of "
            + label
            + (declarations > 1
                ? " and the "
                    + (declarations - 1)
                    + (declarations == 2 ? " declaration" : " declarations")
                    + " it overrides or that override it"
                : "")
            + (none ? " (was " + old.replaceFirst("^extends\\s+", "") + ")" : " to " + type)
            + (calls == null
                ? ""
                : "; " + kept + (kept == 1 ? " call keeps its types" : " calls keep their types"))
            + gated.said(),
        stubs);
  }

  /** The index in {@code list} of the type parameter {@code name}; refused when it has none. */
  private static int typeParameter(List<?> list, String name, String label) {
    List<String> names = new ArrayList<>();
    for (Object o : list) {
      names.add(((TypeParameter) o).getName().getIdentifier());
    }
    if (!names.contains(name)) {
      throw new Refusal(
          label
              + " has no type parameter "
              + name
              + (names.isEmpty()
                  ? "; it has none."
                  : ": it has " + String.join(", ", names) + "."));
    }
    return names.indexOf(name);
  }

  /** The erasure of the type parameter {@code name} declared in {@code declared}, as now parsed. */
  private static String erasureAfter(
      Map<Path, CompilationUnit> after, Map<Path, List<TypeParameter>> declared, String name) {
    for (Map.Entry<Path, List<TypeParameter>> e : declared.entrySet()) {
      CompilationUnit unit = after.get(e.getKey());
      int start = e.getValue().get(0).getStartPosition();
      for (TypeParameter tp : Nodes.all(unit, TypeParameter.class)) {
        if (tp.getStartPosition() == start
            && tp.getName().getIdentifier().equals(name)
            && tp.resolveBinding() != null) {
          return tp.resolveBinding().getErasure().getQualifiedName();
        }
      }
    }
    return null;
  }

  private static String simpleName(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  /** Whether {@code name} names a method declared in {@code file}, by a syntax-only parse. */
  private static boolean namesMethod(Path file, String name, String rel) throws IOException {
    return Files.isRegularFile(file)
        && Declarations.find(Jdt.parseSyntax(Files.readString(file)), name, rel).decl()
            instanceof MethodDeclaration;
  }
}
