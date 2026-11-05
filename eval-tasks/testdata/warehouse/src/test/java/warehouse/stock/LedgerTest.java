package warehouse.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerTest {

  @Test
  void receiveAndShip() {
    Ledger l = new Ledger();
    l.receive(List.of("a1", "a2"), 5);
    l.ship("a1", 2);
    l.giveBack("a1", 1);
    assertEquals(Map.of("dock", -10, "a1", 4, "a2", 5, "bay", 1), l.bins);
    assertEquals("dock>a1:5", l.moves.get(0));
  }

  @Test
  void consolidate() {
    Ledger l = new Ledger();
    l.receive(List.of("a1", "a2", "a3"), 2);
    l.consolidate(List.of("a1", "a2", "a3"), "a2");
    assertEquals(Map.of("dock", -6, "a1", 0, "a2", 6, "a3", 0), l.bins);
  }
}
