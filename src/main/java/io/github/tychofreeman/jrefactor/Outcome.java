package io.github.tychofreeman.jrefactor;

/**
 * What a successful call returned: its text, and the stubs it declared, which the text names but
 * which are carried apart so that a caller can check them.
 */
record Outcome(String text, Stubs.Declared declared) {

  static Outcome of(String text) {
    return new Outcome(text, Stubs.Declared.NONE);
  }

  /** The text the model reads: the call's text with the stubs it declared named after it. */
  String render() {
    return text + declared.text();
  }
}
