package manifests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import freight.Shipment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestTest {
  private static Shipment shipment(String id, String road, String rail) {
    Shipment s = new Shipment(id, "Ann");
    s.road = road;
    s.rail = rail;
    return s;
  }

  @Test
  void linesShowDepotAndYard() {
    Manifest m = new Manifest();
    m.add(shipment("s1", "RD-1", "YARD1"));
    m.add(shipment("s2", "RD-2", null));
    assertEquals(List.of("s1 RD-1/YARD1", "s2 RD-2/-"), m.lines());
    assertEquals(List.of("s2"), m.roadOnly());
    assertEquals(List.of("RD-1", "RD-2"), List.copyOf(m.depots()));
  }

  @Test
  void rerouteMovesTheShipmentsOfADepot() {
    Manifest m = new Manifest();
    Shipment a = shipment("s1", "RD-1", "YARD1");
    Shipment b = shipment("s2", "RD-2", "YARD1");
    m.add(a);
    m.add(b);
    assertEquals(1, m.reroute("RD-1", "RD-9"));
    assertEquals(List.of(a), m.fromDepot("RD-9"));
    assertEquals(2, m.rerail("YARD1", "YARD2"));
    assertEquals(List.of(a, b), m.fromYard("YARD2"));
    assertEquals("YARD2", b.rail);
  }
}