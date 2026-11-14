package io.github.tychofreeman.jrefactor;

import java.util.Map;

/** Java's primitive types as the tools write them: their boxed types, zeros and placeholders. */
final class JavaTypes {

  /** Each primitive type's boxed type, which can be a type argument. */
  static final Map<String, String> BOXED =
      Map.of(
          "int", "Integer",
          "long", "Long",
          "boolean", "Boolean",
          "double", "Double",
          "float", "Float",
          "char", "Character",
          "short", "Short",
          "byte", "Byte");

  private JavaTypes() {}

  /** Whether {@code name} is a primitive type's name. */
  static boolean isPrimitive(String name) {
    return BOXED.containsKey(name);
  }

  /** Whether {@code name} is the simple name of a primitive type's boxed type. */
  static boolean isBox(String name) {
    return BOXED.containsValue(name);
  }

  /** The value a variable of {@code type} starts with: 0, false or null. */
  static String zero(String type) {
    return switch (type) {
      case "int", "long", "short", "byte", "char", "float", "double" -> "0";
      case "boolean" -> "false";
      default -> "null";
    };
  }

  /** A placeholder argument of the type named {@code name}: 0, false, '0', "", or null. */
  static String placeholder(String name) {
    return switch (name) {
      case "boolean" -> "false";
      case "char" -> "'0'";
      case "long" -> "0L";
      case "float" -> "0f";
      case "double" -> "0.0";
      case "byte", "short", "int" -> "0";
      case "String" -> "\"\"";
      default -> "null";
    };
  }
}
