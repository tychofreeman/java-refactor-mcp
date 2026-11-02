package io.github.tychofreeman.jrefactor;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** A tool call's arguments, read with refusals that say what to send. */
final class Args {

  private final String tool;
  private final Map<String, Object> values;

  Args(String tool, Map<String, Object> values) {
    this.tool = tool;
    this.values = values == null ? Map.of() : values;
  }

  /** Refuses an argument the tool does not take, naming the ones it does. */
  void check(Set<String> known) {
    Set<String> unknown = new TreeSet<>(values.keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new Refusal(
          tool
              + " does not take "
              + String.join(", ", unknown)
              + ". Its arguments are "
              + String.join(", ", new TreeSet<>(known))
              + ".");
    }
  }

  String string(String key) {
    Object value = values.get(key);
    if (value == null || value instanceof String) {
      return (String) value;
    }
    throw new Refusal(key + " must be a string; got " + value + ".");
  }

  /** A string that must be given and not empty. */
  String required(String key) {
    String value = string(key);
    if (value == null || value.isEmpty()) {
      throw new Refusal(tool + " needs " + key + ".");
    }
    return value;
  }

  Integer integer(String key) {
    Object value = values.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
      return n.intValue();
    }
    throw new Refusal(key + " must be a whole number; got " + value + ".");
  }

  boolean flag(String key) {
    Object value = values.get(key);
    if (value == null || value instanceof Boolean) {
      return Boolean.TRUE.equals(value);
    }
    throw new Refusal(key + " must be true or false; got " + value + ".");
  }

  boolean has(String key) {
    return values.containsKey(key);
  }
}
