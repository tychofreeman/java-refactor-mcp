package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;

/**
 * A name of the form {@code Type.member}: the type, top-level or nested ({@code Outer.Inner}), and
 * the member as written, which may end in its parameter types ({@code m(Map.Entry, int)}). The name
 * splits at its last dot before the parameter list; the type is null when it has none.
 */
record Qualified(String type, String member) {

  /**
   * {@code name} split; refused when it has no type. {@code shape} says what it must be, as the
   * refusal names it: "Type.field, the class and the field to encapsulate".
   */
  static Qualified of(String name, String shape) {
    Qualified q = parse(name);
    if (q.type() == null) {
      throw new Refusal("name is " + shape + ", not " + name + ".");
    }
    return q;
  }

  /** {@code name} split, its type null when it names none; refused when a parenthesis is open. */
  static Qualified parse(String name) {
    String written = name.strip();
    int paren = written.indexOf('(');
    if (paren >= 0 && !written.endsWith(")")) {
      throw new Refusal("name " + name + " has an unclosed parenthesis.");
    }
    int dot = written.lastIndexOf('.', paren < 0 ? written.length() : paren);
    return dot < 0
        ? new Qualified(null, written)
        : new Qualified(written.substring(0, dot).strip(), written.substring(dot + 1).strip());
  }

  /** The member's name, without its parameter types. */
  String name() {
    int paren = member.indexOf('(');
    return paren < 0 ? member : member.substring(0, paren).strip();
  }

  /**
   * The member's parameter types, each without spaces ({@code Map<String,Integer>}, {@code
   * int...}), or null when it has no parameter list.
   */
  List<String> params() {
    int paren = member.indexOf('(');
    if (paren < 0) {
      return null;
    }
    String inside = member.substring(paren + 1, member.length() - 1).strip();
    List<String> params = new ArrayList<>();
    for (String p : inside.isEmpty() ? List.<String>of() : topLevelCommas(inside)) {
      params.add(p.replace(" ", ""));
    }
    return params;
  }

  /**
   * The parts of a parameter list split at its commas, outside any {@code <...>} or {@code (...)}.
   */
  static List<String> topLevelCommas(String list) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int from = 0;
    for (int i = 0; i < list.length(); i++) {
      char c = list.charAt(i);
      if (c == '<' || c == '(') {
        depth++;
      } else if (c == '>' || c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(list.substring(from, i));
        from = i + 1;
      }
    }
    parts.add(list.substring(from));
    return parts;
  }
}
