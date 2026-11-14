package io.github.tychofreeman.jrefactor;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.lang.model.SourceVersion;

/**
 * Something a refactoring needs before it plans an edit, as a value: whether it holds, and the
 * refusal when it does not. Both are evaluated only when checked, in order, so a precondition may
 * assume the ones listed before it hold.
 */
record Precondition(BooleanSupplier holds, Supplier<String> refusal) {

  static Precondition that(BooleanSupplier holds, Supplier<String> refusal) {
    return new Precondition(holds, refusal);
  }

  /** Refuses with the first of {@code checks} that does not hold. */
  static void require(Precondition... checks) {
    for (Precondition check : checks) {
      if (!check.holds().getAsBoolean()) {
        throw new Refusal(check.refusal().get());
      }
    }
  }

  /**
   * Holds when {@code name} is one Java identifier, not a keyword: the name of {@code what}, such
   * as "the new method".
   */
  static Precondition identifier(String name, String what) {
    return that(
        () -> SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name),
        () ->
            name
                + " is not a valid Java name for "
                + what
                + ": use letters, digits, '_' or '$', not starting with a digit, and not a keyword.");
  }
}
