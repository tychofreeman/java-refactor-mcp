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

  /**
   * A refusal of a call that keeps what it did before the refused part: a batch refused after a
   * check step passed keeps the steps through it.
   */
  static final class Kept extends Refusal {
    Kept(String message) {
      super(message);
    }
  }
}
