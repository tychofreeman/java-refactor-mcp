package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AcceptTest {
  private final Book book = new Book("1", "Tide");
  private final Member member = new Member("m1", "Ann");
  private final LocalDate day = LocalDate.of(2026, 1, 1);

  @Test
  void aReservationHoldsWhatItWasMadeWith() {
    Reservation r = new Reservation(book, member, day);
    assertEquals(book, r.book());
    assertEquals(member, r.member());
    assertEquals(day, r.due());
  }

  @Test
  void aRenewalHoldsWhatItWasMadeWith() {
    Renewal r = new Renewal(book, day);
    assertEquals(book, r.book());
    assertEquals(day, r.until());
  }

  @Test
  void aHoldHoldsWhatItWasMadeWith() {
    Hold h = new Hold(member, book, 3);
    assertEquals(member, h.member());
    assertEquals(book, h.book());
    assertEquals(3, h.position());
  }
}
