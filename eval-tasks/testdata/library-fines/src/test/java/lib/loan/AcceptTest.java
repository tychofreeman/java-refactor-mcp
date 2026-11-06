package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AcceptTest {
  @Test
  void theFirstThreeDaysAreFree() {
    assertEquals(0, Fines.fine(1));
    assertEquals(0, Fines.fine(3));
  }

  @Test
  void eachDayAfterCosts25() {
    assertEquals(25, Fines.fine(4));
    assertEquals(175, Fines.fine(10));
  }

  @Test
  void theFineIsAtMost500() {
    assertEquals(500, Fines.fine(100));
  }

  @Test
  void earlyIsFree() {
    assertEquals(0, Fines.fine(-2));
  }
}
