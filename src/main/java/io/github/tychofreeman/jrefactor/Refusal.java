package io.github.tychofreeman.jrefactor;

/**
 * A refactoring that cannot be done as asked. The message is written for the model: it says why,
 * and what to try instead. Nothing has been changed when this is thrown.
 */
public class Refusal extends RuntimeException {

  public Refusal(String message) {
    super(message);
  }
}
