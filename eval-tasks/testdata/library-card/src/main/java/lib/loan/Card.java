package lib.loan;

/** A member's library card: who holds it, and how many books they may borrow at once. */
public class Card {
  private Member holder;
  private int limit;

  /** The member who holds this card. */
  public Member holder() {
    return holder;
  }

  /** How many books the holder may borrow at once. */
  public int limit() {
    return limit;
  }
}
