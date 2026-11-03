package io.github.tychofreeman.jrefactor;

/**
 * A refactoring that cannot be done as asked. The message is written for the model: it says why,
 * and what to try instead. Nothing has been changed when this is thrown.
 */
public class Refusal extends RuntimeException {

  /**
   * What the server writes before a refusal's message, and what {@link SessionReport} looks for.
   */
  static final String PREFIX = "refused: ";

  public Refusal(String message) {
    super(message);
  }
}
