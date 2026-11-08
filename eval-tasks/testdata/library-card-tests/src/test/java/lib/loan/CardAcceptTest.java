package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CardAcceptTest {
  private final Member ann = new Member("m1", "Ann");

  @Test
  void holdsItsMemberAndLimit() {
    Card card = new Card(ann, 5);
    assertEquals(ann, card.holder());
    assertEquals(5, card.limit());
    assertEquals(0, card.borrowed());
  }

  @Test
  void borrowsUpToTheLimit() {
    Card card = new Card(ann, 2);
    assertTrue(card.borrow());
    assertTrue(card.borrow());
    assertFalse(card.borrow());
    assertEquals(2, card.borrowed());
  }
}
