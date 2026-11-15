package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;

/**
 * gorefactor's names for operations this server publishes under others (issue #372), each run as
 * the tool here with its arguments mapped, so a recipe or batch written for gorefactor runs here.
 */
final class GoNames {

  private GoNames() {}

  /** introduce_parameter: extract_variable with as=parameter. */
  static Outcome introduceParameter(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String expression = args.required("expression");
    Map<String, Object> to = new LinkedHashMap<>();
    to.put("file", rel);
    to.put("line", line(project, args, rel, expression));
    to.put("expression", expression);
    to.put("name", args.required("parameter"));
    to.put("as", "parameter");
    copy(args, to, "column", Exceptional.FLAG);
    return Tools.call(Tools.named("extract_variable"), project, to);
  }

  /** convert_params_to_object: extract_type of every parameter of the method. */
  static Outcome paramsToObject(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    String text = Files.readString(project.javaFile(rel));
    BodyDeclaration declared = Declarations.find(Edits.parses(text, rel), name, rel).decl();
    if (!(declared instanceof MethodDeclaration m)) {
      throw new Refusal(name + " is not a method; convert_params_to_object takes a method's name.");
    }
    List<String> members = new ArrayList<>();
    for (Object p : m.parameters()) {
      members.add(((SingleVariableDeclaration) p).getName().getIdentifier());
    }
    String method = m.getName().getIdentifier();
    String type = args.string("type_name");
    if (type == null) {
      type = Character.toUpperCase(method.charAt(0)) + method.substring(1) + "Args";
    }
    Map<String, Object> to = new LinkedHashMap<>();
    to.put("file", rel);
    to.put("name", name);
    to.put("members", members);
    to.put("new_type", type);
    to.put("as", Character.toLowerCase(type.charAt(0)) + type.substring(1));
    copy(args, to, Exceptional.FLAG);
    return Tools.call(Tools.named("extract_type"), project, to);
  }

  /**
   * remove_dead_code: replace_declaration with empty content, a safe delete; with also, every one
   * of them in one batch checked at the end, so uses among them do not count.
   */
  static Outcome removeDeadCode(SourceProject project, Args args) throws Exception {
    if (args.string("value") != null) {
      throw new Refusal(
          "value (a field's reads written as a path of other fields) is not built here; change"
              + " the reads first, then remove the field.");
    }
    String rel = args.required("file");
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("file", rel);
    first.put("name", args.required("name"));
    first.put("content", "");
    copy(args, first, "line", "column");
    List<String> also = args.list("also");
    if (also.isEmpty()) {
      return Tools.call(Tools.named("replace_declaration"), project, first);
    }
    List<Map<String, Object>> steps = new ArrayList<>();
    steps.add(Map.of("tool", "replace_declaration", "args", first));
    for (String name : also) {
      steps.add(
          Map.of(
              "tool",
              "replace_declaration",
              "args",
              Map.of("file", rel, "name", name, "content", "")));
    }
    return Tools.call(Tools.named("batch"), project, Map.of("steps", steps, "check", "end"));
  }

  /** prepare_switch_for_types: type_switch_to_method, which moves the cases in one call here. */
  static Outcome prepareSwitch(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    Integer line = args.integer("line");
    String function = args.string("function");
    if ((line == null) == (function == null)) {
      throw new Refusal(
          "prepare_switch_for_types needs line (where the switch starts) or function (the method"
              + " whose only switch it is), one of them.");
    }
    if (function != null) {
      String text = Files.readString(project.javaFile(rel));
      BodyDeclaration declared = Declarations.find(Edits.parses(text, rel), function, rel).decl();
      List<ASTNode> switches = new ArrayList<>();
      switches.addAll(Nodes.all(declared, SwitchStatement.class));
      switches.addAll(Nodes.all(declared, SwitchExpression.class));
      if (switches.size() != 1) {
        throw new Refusal(
            function
                + " has "
                + switches.size()
                + " switches; give line, where the one to move starts.");
      }
      line = Lines.number(text, switches.get(0).getStartPosition());
    }
    Map<String, Object> to = new LinkedHashMap<>();
    to.put("file", rel);
    to.put("line", line);
    to.put("name", args.required("method"));
    copy(args, to, Exceptional.FLAG);
    return Tools.call(Tools.named("type_switch_to_method"), project, to);
  }

  /** The line {@code expression} starts on: line, or its first use in the body of function. */
  private static int line(SourceProject project, Args args, String rel, String expression)
      throws Exception {
    Integer line = args.integer("line");
    String function = args.string("function");
    if ((line == null) == (function == null)) {
      throw new Refusal(
          "introduce_parameter needs line (where the expression starts) or function (the method"
              + " whose body uses it), one of them.");
    }
    if (line != null) {
      return line;
    }
    String text = Files.readString(project.javaFile(rel));
    BodyDeclaration declared = Declarations.find(Edits.parses(text, rel), function, rel).decl();
    ASTNode body =
        declared instanceof MethodDeclaration m && m.getBody() != null ? m.getBody() : declared;
    int at = text.indexOf(expression, body.getStartPosition());
    if (at < 0 || at + expression.length() > Nodes.end(body)) {
      throw new Refusal(
          expression + " is not in the body of " + function + ". Copy it from the method.");
    }
    return Lines.number(text, at);
  }

  /** Each of {@code keys} given in {@code args}, put in {@code to} as sent. */
  private static void copy(Args args, Map<String, Object> to, String... keys) {
    for (String key : keys) {
      if (args.has(key)) {
        to.put(key, args.raw(key));
      }
    }
  }
}
