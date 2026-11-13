package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/**
 * add_parameter with {@code name} an enum: a field with a value per constant. The enum gets {@code
 * private final <type> <parameter>}, its constructor takes and assigns it (one is written when the
 * enum declares none), an accessor of the parameter's name returns it, and each constant passes its
 * entry of {@code values}, else {@code value}. Nothing reads the field yet, so behavior is
 * unchanged.
 */
final class EnumField {
  private EnumField() {}

  /** The field added to the enum {@code args} name, or null when {@code name} is no enum. */
  static Outcome add(SourceProject project, Args args) throws Exception {
    String name = args.string("name");
    Opened opened = Opened.of(project, args.required("file"));
    EnumDeclaration target = null;
    for (EnumDeclaration e : Nodes.all(opened.unit(), EnumDeclaration.class)) {
      ITypeBinding b = e.resolveBinding();
      if (b != null && !b.isLocal() && Names.typeLabel(b).equals(name)) {
        target = e;
      }
    }
    if (target == null) {
      if (args.has("values")) {
        throw new Refusal(
            "values gives each constant of an enum its own value, and "
                + name
                + " is not an enum of "
                + opened.rel()
                + ". Pass value, which every call passes.");
      }
      return null;
    }
    String parameter = args.required("parameter");
    String type = args.required("type");
    if (!SourceVersion.isName(parameter)) {
      throw new Refusal("parameter " + parameter + " is not a valid Java name.");
    }
    Signatures.parsesAs("type", type, "class X { " + type + " x; }");
    Map<String, String> passed = passed(target, name, args);
    String text = opened.text();
    String before = args.string("before");
    Field fieldResult = field(target, parameter, before, passed, text, type);
    List<TextEdit> edits = fieldResult.edits();
    String members = fieldResult.members();
    edits.sort((a, b) -> b.start() - a.start());
    String withValues = TextEdit.apply(text, edits, 0);
    String out = Declarations.plan(withValues, opened.rel(), name, members).text();

    int constants = target.enumConstants().size();
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            out,
            args.list("imports"),
            "adding the field " + type + " " + parameter + " to " + name,
            "Check that each value type-checks as " + type + " where the constants are declared.");
    return new Outcome(
        "added the field "
            + type
            + " "
            + parameter
            + " to "
            + name
            + ", set by its constructor and read by "
            + parameter
            + "(); "
            + constants
            + (constants == 1 ? " constant passes its value" : " constants pass their values")
            + written.fixed().said(),
        written.declared());
  }

  /**
   * The field {@code type parameter} on {@code target}, an enum of {@code text}: the edits that
   * pass each constant its entry of {@code passed} and add the parameter to the constructor (before
   * {@code before}, else last), and the members to add after them (the field, the constructor when
   * the enum declares none, the accessor). Refuses a name the enum has or inherits.
   */
  static Field field(
      EnumDeclaration target,
      String parameter,
      String before,
      Map<String, String> passed,
      String text,
      String type) {
    String simple = target.getName().getIdentifier();
    MethodDeclaration constructor = constructor(target, simple);
    checkName(target.resolveBinding(), simple, parameter, constructor);
    List<TextEdit> edits = new ArrayList<>();
    int at = index(constructor, simple, before);
    for (Object o : target.enumConstants()) {
      EnumConstantDeclaration c = (EnumConstantDeclaration) o;
      String value = passed.get(c.getName().getIdentifier());
      int end = Nodes.end(c.getName());
      int open = Signatures.after(text, end);
      if (open == 0 || !text.substring(end, open - 1).isBlank()) {
        edits.add(new TextEdit(end, end, "(" + value + ")"));
      } else {
        edits.add(Signatures.insertAt(text, c.arguments(), open - 1, at, value));
      }
    }
    int step = Declarations.step(text) > 0 ? Declarations.step(text) : 2;
    String in = " ".repeat(step);
    String assign = "this." + parameter + " = " + parameter + ";";
    String members = "private final " + type + " " + parameter + ";\n\n";
    if (constructor == null) {
      members += simple + "(" + type + " " + parameter + ") {\n" + in + assign + "\n}\n\n";
    } else {
      edits.add(
          Signatures.insertAt(
              text,
              constructor.parameters(),
              Signatures.open(text, constructor.getName()) - 1,
              at,
              type + " " + parameter));
      edits.add(assignment(text, constructor.getBody(), assign, in));
    }
    members += "public " + type + " " + parameter + "() {\n" + in + "return " + parameter + ";\n}";
    return new Field(edits, members);
  }

  /** A field's edits to an enum's text, and the members to add after them. */
  record Field(List<TextEdit> edits, String members) {}

  /** The enum's only constructor, or null when it declares none; several are refused. */
  private static MethodDeclaration constructor(EnumDeclaration target, String simple) {
    List<MethodDeclaration> constructors = new ArrayList<>();
    for (Object o : target.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m && m.isConstructor()) {
        constructors.add(m);
      }
    }
    if (constructors.size() > 1) {
      List<String> labels = constructors.stream().map(MemberMove::label).toList();
      throw new Refusal(
          simple
              + " has "
              + constructors.size()
              + " constructors ("
              + String.join(", ", labels)
              + "), and each constant chooses one. add_parameter adds a field to an enum with at"
              + " most one constructor; to add a plain parameter to one of them, name it: "
              + labels.get(0)
              + ".");
    }
    return constructors.isEmpty() ? null : constructors.get(0);
  }

  /**
   * Refuses {@code parameter} when the enum has a field or a parameter of its constructor of that
   * name, or declares or inherits a method of that name without parameters, which the accessor
   * would be.
   */
  private static void checkName(
      ITypeBinding type, String simple, String parameter, MethodDeclaration constructor) {
    for (IVariableBinding f : type.getDeclaredFields()) {
      if (f.getName().equals(parameter)) {
        throw new Refusal(simple + " already has a field " + parameter + ".");
      }
    }
    if (constructor != null) {
      for (Object o : constructor.parameters()) {
        if (((SingleVariableDeclaration) o).getName().getIdentifier().equals(parameter)) {
          throw new Refusal(simple + "'s constructor already has a parameter " + parameter + ".");
        }
      }
    }
    for (IMethodBinding m : type.getDeclaredMethods()) {
      if (m.getName().equals(parameter) && m.getParameterTypes().length == 0) {
        throw new Refusal(simple + " already has a method " + parameter + "().");
      }
    }
    List<ITypeBinding> supertypes = new ArrayList<>();
    supertypes.add(type.getSuperclass());
    supertypes.addAll(List.of(type.getInterfaces()));
    for (int i = 0; i < supertypes.size(); i++) {
      ITypeBinding s = supertypes.get(i);
      if (s == null) {
        continue;
      }
      for (IMethodBinding m : s.getDeclaredMethods()) {
        if (m.getName().equals(parameter) && m.getParameterTypes().length == 0) {
          throw new Refusal(
              simple
                  + " inherits "
                  + parameter
                  + "() from "
                  + s.getErasure().getName()
                  + "; choose another name.");
        }
      }
      supertypes.add(s.getSuperclass());
      supertypes.addAll(List.of(s.getInterfaces()));
    }
  }

  /** The value each constant passes: its entry of {@code values}, else {@code value}. */
  private static Map<String, String> passed(EnumDeclaration target, String name, Args args) {
    Map<String, String> values = args.strings("values");
    String rest = args.string("value");
    List<String> constants = new ArrayList<>();
    for (Object o : target.enumConstants()) {
      constants.add(((EnumConstantDeclaration) o).getName().getIdentifier());
    }
    for (String key : values.keySet()) {
      if (!constants.contains(key)) {
        throw new Refusal(
            name
                + " has no constant "
                + key
                + "; its constants are "
                + String.join(", ", constants)
                + ".");
      }
    }
    Map<String, String> passed = new LinkedHashMap<>();
    for (String constant : constants) {
      String value = values.getOrDefault(constant, rest);
      if (value == null || value.isEmpty()) {
        throw new Refusal(
            "values has nothing for "
                + constant
                + ", and there is no value for the rest. Add "
                + constant
                + " to values, or pass value for every constant values does not name.");
      }
      Signatures.parsesAs(
          values.containsKey(constant) ? "the value of " + constant : "value",
          value,
          "class X { Object x = " + value + "; }");
      passed.put(constant, value);
    }
    return passed;
  }

  /** Where the parameter goes among the constructor's: before {@code before}, else last. */
  private static int index(MethodDeclaration constructor, String simple, String before) {
    List<?> parameters = constructor == null ? List.of() : constructor.parameters();
    if (before == null) {
      return parameters.size();
    }
    for (int i = 0; i < parameters.size(); i++) {
      if (((SingleVariableDeclaration) parameters.get(i))
          .getName()
          .getIdentifier()
          .equals(before)) {
        return i;
      }
    }
    throw new Refusal(simple + "'s constructor has no parameter " + before + " to put it before.");
  }

  /** The edit that puts {@code assign} last in {@code body}, a statement on a line of its own. */
  private static TextEdit assignment(String text, Block body, String assign, String in) {
    int close = Nodes.end(body) - 1;
    int line = Lines.start(text, close);
    if (text.substring(line, close).isBlank()) {
      List<?> statements = body.statements();
      String indent =
          statements.isEmpty()
              ? text.substring(line, close) + in
              : Lines.indentOf(
                  text, ((ASTNode) statements.get(statements.size() - 1)).getStartPosition());
      return new TextEdit(line, line, indent + assign + "\n");
    }
    if (body.statements().isEmpty()) {
      String indent = Lines.indentOf(text, body.getParent().getStartPosition());
      return new TextEdit(
          body.getStartPosition() + 1, close, "\n" + indent + in + assign + "\n" + indent);
    }
    // The body is on one line with its statements: the assignment joins them.
    int last = Nodes.end((ASTNode) body.statements().get(body.statements().size() - 1));
    return new TextEdit(last, last, " " + assign);
  }
}
