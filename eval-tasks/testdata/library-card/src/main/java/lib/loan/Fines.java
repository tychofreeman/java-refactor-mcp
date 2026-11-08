package lib.loan;

/** What a member pays for a book returned late. */
public final class Fines {
  private Fines() {}

  /** The fine in cents for a book returned {@code daysLate} days late. */
  public static int fine(int daysLate) {
    return Math.max(0, daysLate) * 25;
  }
}
