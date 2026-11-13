package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jdt.core.dom.FieldDeclaration;
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
              + " is not a class. add_parameter with type_parameter takes the name of a class of "
              + rel
              + ".");
    }
    return toClass(project, args, holding, file, type, named.label(), written, parameter, value);
  }

  /** The type argument every use passes: its text, as any file can write it. */
  private record Value(String text) {}

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
          list.add(inserted(p.typeArguments(), at, argument));
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

    Map<Path, String> texts = new LinkedHashMap<>();
    List<String> notes = new ArrayList<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      if (e.getValue().isEmpty()) {
        continue;
      }
      String old = holding.texts().get(e.getKey());
      Imports.Fixed fixed =
          Imports.fix(project, e.getKey(), old, TextEdit.apply(old, e.getValue(), 0), chosen);
      texts.put(e.getKey(), fixed.text());
      if (!fixed.notes().isEmpty()) {
        notes.add(project.relative(e.getKey()) + ": " + String.join("; ", fixed.notes()));
      }
    }
    CallTargets targets = CallTargets.of(holding.units().values());
    String fix =
        "Change the uses that do not compile with "
            + target.getName().getIdentifier()
            + "<"
            + v.text()
            + "> first, or choose another value.";
    Stubs.Declared stubs = Gate.applyOrRefuse(project, texts, what, fix);
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
            + (notes.isEmpty() ? "" : "; " + String.join("; ", notes)),
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
    return inserted(target.typeParameters(), at, written);
  }

  /** The edit that puts {@code text} at {@code at} of {@code list}, which is not empty. */
  private static TextEdit inserted(List<?> list, int at, String text) {
    if (at >= list.size()) {
      int end = Nodes.end((ASTNode) list.get(list.size() - 1));
      return new TextEdit(end, end, ", " + text);
    }
    int start = ((ASTNode) list.get(at)).getStartPosition();
    return new TextEdit(start, start, text + ", ");
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
        return new Value(Extract.typeName(t));
      }
    }
    return new Value(value);
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
}
