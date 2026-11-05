package io.github.tychofreeman.jrefactor;

import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One argument of a tool: its name, its JSON schema and whether it is required. A {@link Tools.Def}
 * lists its arguments, and its schema and argument names are built from them.
 */
record Arg(String name, ObjectNode schema, boolean mandatory) {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The Java file a tool works on. */
  static final Arg FILE = string("file", "Java file path relative to the project root.").required();

  /** The names to import where a simple name is ambiguous. */
  static final Arg IMPORTS =
      strings(
          "imports", "Optional: fully qualified names to import where a simple name is ambiguous.");

  static Arg string(String name, String description) {
    return of(name, "string", description);
  }

  static Arg integer(String name, String description) {
    return of(name, "integer", description);
  }

  static Arg bool(String name, String description) {
    return of(name, "boolean", description);
  }

  /** A string that is one of {@code values}. */
  static Arg oneOf(String name, String description, String... values) {
    Arg a = string(name, description);
    ArrayNode e = a.schema.putArray("enum");
    for (String v : values) {
      e.add(v);
    }
    return a;
  }

  /** A list of strings. */
  static Arg strings(String name, String description) {
    Arg a = of(name, "array", description);
    a.schema.putObject("items").put("type", "string");
    return a;
  }

  /** An argument whose schema is written out as JSON. */
  static Arg json(String name, String schema) {
    return new Arg(name, (ObjectNode) JSON.readTree(schema), false);
  }

  private static Arg of(String name, String type, String description) {
    ObjectNode s = JSON.createObjectNode();
    s.put("type", type);
    s.put("description", description);
    return new Arg(name, s, false);
  }

  /** This argument, required. */
  Arg required() {
    return new Arg(name, schema, true);
  }

  /** This argument with another description. */
  Arg described(String description) {
    ObjectNode s = schema.deepCopy();
    s.put("description", description);
    return new Arg(name, s, mandatory);
  }

  /** The schema of a tool that takes {@code args}, as JSON text. */
  static String schemaOf(List<Arg> args) {
    ObjectNode root = JSON.createObjectNode();
    root.put("type", "object");
    ObjectNode properties = root.putObject("properties");
    ArrayNode required = JSON.createArrayNode();
    for (Arg a : args) {
      properties.set(a.name, a.schema);
      if (a.mandatory) {
        required.add(a.name);
      }
    }
    if (!required.isEmpty()) {
      root.set("required", required);
    }
    return root.toString();
  }
}
