package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

  /** A list of strings; empty when not given. */
  List<String> list(String key) {
    Object value = values.get(key);
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> items && items.stream().allMatch(i -> i instanceof String)) {
      return items.stream().map(i -> (String) i).toList();
    }
    throw new Refusal(key + " must be a list of strings; got " + value + ".");
  }

  /** A list of JSON objects, as maps; empty when not given. */
  List<Map<String, Object>> objects(String key) {
    Object value = values.get(key);
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> items && items.stream().allMatch(i -> i instanceof Map)) {
      List<Map<String, Object>> maps = new ArrayList<>();
      for (Object item : items) {
        Map<String, Object> map = new LinkedHashMap<>();
        ((Map<?, ?>) item).forEach((k, v) -> map.put(String.valueOf(k), v));
        maps.add(map);
      }
      return maps;
    }
    throw new Refusal(key + " must be a list of objects; got " + value + ".");
  }

  boolean has(String key) {
    return values.containsKey(key);
  }

  /** The tool these arguments are for. */
  String tool() {
    return tool;
  }

  /**
   * These arguments with each key of {@code renames} that is given, and whose new name is not,
   * under its new name: old names a tool still accepts.
   */
  Args renamed(Map<String, String> renames) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    for (Map.Entry<String, String> r : renames.entrySet()) {
      if (out.containsKey(r.getKey()) && !out.containsKey(r.getValue())) {
        out.put(r.getValue(), out.remove(r.getKey()));
      }
    }
    return new Args(tool, out);
  }

  /** These arguments without {@code names}: ones a tool still accepts and ignores. */
  Args without(Set<String> names) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    out.keySet().removeAll(names);
    return new Args(tool, out);
  }

  /** These arguments with {@code key} set to {@code value}. */
  Args with(String key, Object value) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    out.put(key, value);
    return new Args(tool, out);
  }
}
