package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** A tool call's arguments, read with refusals that say what to send. */
final class Args {

  /** The arguments that may be sent empty: an empty content deletes, an empty new removes. */
  private static final Set<String> MAY_BE_EMPTY = Set.of("content", "new");

  private final String tool;
  private final Map<String, Object> values;
  private final List<Arg> arguments;

  Args(String tool, Map<String, Object> values) {
    this(tool, values, List.of());
  }

  /** {@code values}, read with the words of {@code arguments}, the tool's, in refusals. */
  Args(String tool, Map<String, Object> values, List<Arg> arguments) {
    this.tool = tool;
    this.values = values == null ? Map.of() : values;
    this.arguments = arguments;
  }

  /**
   * Refuses an argument the tool does not take, naming the ones it does, and a required one not
   * given, in the words of {@link Arg#missing}.
   */
  void check(List<Arg> arguments) {
    Set<String> known = new TreeSet<>();
    for (Arg a : arguments) {
      known.add(a.name());
    }
    Set<String> unknown = new TreeSet<>(values.keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new Refusal(
          tool
              + " does not take "
              + String.join(", ", unknown)
              + ". Its arguments are "
              + String.join(", ", known)
              + ".");
    }
    List<String> missing = new ArrayList<>();
    for (Arg a : arguments) {
      if (a.mandatory() && unset(a.name())) {
        missing.add(a.missing(tool));
      }
    }
    if (!missing.isEmpty()) {
      throw new Refusal(String.join(" ", missing));
    }
  }

  String string(String key) {
    Object value = values.get(key);
    if (value == null || value instanceof String) {
      return (String) value;
    }
    throw new Refusal(key + " must be a string; got " + value + ".");
  }

  /** A string that must be given and not blank, refused in the words of {@link Arg#missing}. */
  String required(String key) {
    String value = string(key);
    if (value == null || value.isBlank()) {
      throw new Refusal(argument(key).missing(tool));
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
    return new Args(tool, out, arguments);
  }

  /** These arguments without {@code names}: ones a tool still accepts and ignores. */
  Args without(Set<String> names) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    out.keySet().removeAll(names);
    return new Args(tool, out, arguments);
  }

  /** These arguments with {@code key} set to {@code value}. */
  Args with(String key, Object value) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    out.put(key, value);
    return new Args(tool, out, arguments);
  }

  /** The value of {@code key} as it was sent (a list, an object), or null when not given. */
  Object raw(String key) {
    return values.get(key);
  }

  /**
   * A JSON object whose values are strings (a number or true/false as its text); empty when not
   * given.
   */
  Map<String, String> strings(String key) {
    Object value = values.get(key);
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map<?, ?> map
        && map.values().stream()
            .allMatch(v -> v instanceof String || v instanceof Number || v instanceof Boolean)) {
      Map<String, String> strings = new LinkedHashMap<>();
      map.forEach((k, v) -> strings.put(String.valueOf(k), String.valueOf(v)));
      return strings;
    }
    throw new Refusal(key + " must be an object of strings; got " + value + ".");
  }

  /**
   * These arguments for {@code tool}, which a router hands them to: only those {@code takes} names,
   * and only those given a value.
   */
  Args routed(String tool, Set<String> takes) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(values);
    out.keySet().retainAll(takes);
    out.values().removeIf(v -> v == null);
    return new Args(tool, out, arguments.stream().filter(a -> takes.contains(a.name())).toList());
  }

  /** The arguments as sent, by name. */
  Map<String, Object> values() {
    return values;
  }

  /** The names in a comma-separated list, none for null. */
  static List<String> commaSeparated(String list) {
    List<String> out = new ArrayList<>();
    if (list != null) {
      for (String s : list.split(",")) {
        if (!s.isBlank()) {
          out.add(s.strip());
        }
      }
    }
    return out;
  }

  /**
   * These arguments for {@code tool}, which takes {@code takes}: as the other routed, in its words.
   */
  Args routed(String tool, List<Arg> takes) {
    Args out = routed(tool, takes.stream().map(Arg::name).collect(Collectors.toSet()));
    return new Args(tool, out.values, takes);
  }

  /** Whether {@code key} is not given, or given blank when it is not one that may be empty. */
  private boolean unset(String key) {
    Object value = values.get(key);
    return value == null
        || (value instanceof String s && s.isBlank() && !MAY_BE_EMPTY.contains(key));
  }

  /** The tool's argument {@code key}; one without a description when the tool's are not known. */
  private Arg argument(String key) {
    return arguments.stream()
        .filter(a -> a.name().equals(key))
        .findFirst()
        .orElse(Arg.string(key, ""));
  }
}
