package depots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import freight.Shipment;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DepotsTest {
  private final Depots depots = new Depots(Map.of("north", "RD-N"), Map.of("north", "YARD-N"));

  @Test
  void assignFillsOnlyWhatIsMissing() {
    Shipment s = new Shipment("s1", "Ann");
    s.rail = "YARD-X";
    depots.assign(s, "north");
    assertEquals("RD-N", s.road);
    assertEquals("YARD-X", s.rail);
    depots.prune(s);
    assertEquals("RD-N", s.road);
    assertNull(s.rail);
  }

  @Test
  void swapExchangesBothPickups() {
    Shipment a = new Shipment("a", "Ann");
    a.road = " rd-1 ";
    Shipment b = new Shipment("b", "Bo");
    b.rail = "YARD2";
    Depots.swap(a, b);
    assertNull(a.road);
    assertEquals("YARD2", a.rail);
    Depots.normalize(b);
    assertEquals("RD-1", b.road);
    assertTrue(Depots.together(b, b.copy("c")));
  }
}