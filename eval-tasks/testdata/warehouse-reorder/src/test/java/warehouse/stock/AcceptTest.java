package warehouse.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptTest {

  @Test
  void transferOrder() {
    Ledger l = new Ledger();
    l.transfer("a1", "b1", 3);
    assertEquals(Map.of("a1", 3, "b1", -3), l.bins, "transfer(to, from, qty)");
  }

  @Test
  void apply() {
    Ledger l = new Ledger();
    l.apply(PickList.parse("a1 b1 3 b1 c1 1"));
    assertEquals(Map.of("a1", -3, "b1", 2, "c1", 1), l.bins);
    assertEquals(List.of("a1>b1:3", "b1>c1:1"), l.moves);
  }

  @Test
  void consolidate() {
    Ledger l = new Ledger();
    l.receive(List.of("a1", "a2"), 2);
    l.consolidate(List.of("a1", "a2"), "a2");
    assertEquals(Map.of("dock", -4, "a1", 0, "a2", 4), l.bins);
    assertEquals("a1>a2:2", l.moves.get(2));
  }
}
