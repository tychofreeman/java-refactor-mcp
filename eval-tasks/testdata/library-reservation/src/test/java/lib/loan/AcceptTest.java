package lib.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptTest {
  private final Book book = new Book("1", "Tide");
  private final Member member = new Member("m1", "Ann");

  @Test
  void aReservationHoldsWhatItWasMadeWith() {
    LocalDate due = LocalDate.of(2026, 1, 1);
    Reservation r = new Reservation(book, member, due);
    assertEquals(book, r.book());
    assertEquals(member, r.member());
    assertEquals(due, r.due());
  }

  @Test
  void reserveIsDueFourteenDaysLater() {
    Reservation r = new Library(Map.of("1", 1)).reserve(book, member, LocalDate.of(2026, 1, 1));
    assertEquals(LocalDate.of(2026, 1, 15), r.due());
    assertEquals(book, r.book());
    assertEquals(member, r.member());
  }
}
