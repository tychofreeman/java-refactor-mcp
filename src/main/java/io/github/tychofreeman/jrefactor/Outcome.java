package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;

/**
 * What a successful call returned: its text, the stubs it declared, which the text names but which
 * are carried apart so that a caller can check them, and the changes it made to exceptional
 * behavior, which the model reads apart from the text (a field of their own).
 */
record Outcome(String text, Stubs.Declared declared, List<String> exceptional) {

  Outcome(String text, Stubs.Declared declared) {
    this(text, declared, List.of());
  }

  static Outcome of(String text) {
    return new Outcome(text, Stubs.Declared.NONE);
  }

  /** This outcome with {@code more} changes to exceptional behavior after its own. */
  Outcome withExceptional(List<String> more) {
    List<String> all = new ArrayList<>(exceptional);
    all.addAll(more);
    return new Outcome(text, declared, List.copyOf(all));
  }

  /** The text the model reads: the call's text with the stubs it declared named after it. */
  String render() {
    return text + declared.text();
  }

  /** The changes to exceptional behavior, as the model reads them; empty when there are none. */
  String renderExceptional() {
    if (exceptional.isEmpty()) {
      return "";
    }
    return "exceptional behavior changed (pass "
        + Exceptional.FLAG
        + "=true to keep it, or to be refused):\n- "
        + String.join("\n- ", exceptional);
  }

  /**
   * The parts of the result: the text, and the changes to exceptional behavior when there are any.
   */
  List<String> contents() {
    return exceptional.isEmpty() ? List.of(render()) : List.of(render(), renderExceptional());
  }
}
