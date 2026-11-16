package audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import freight.Shipment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditTest {
  @Test
  void problemsNameTheBadCodes() {
    Shipment s = new Shipment("s1", "Ann");
    assertEquals(List.of("s1: no pickup"), Audit.problems(s));
    s.road = "DEPOT-1";
    s.rail = "YARD1";
    assertEquals(List.of("s1: road code DEPOT-1"), Audit.problems(s));
    assertEquals("s1 road=DEPOT-1 rail=YARD1 air=null", Audit.describe(s));
  }

  @Test
  void countsShipmentsByDepot() {
    Shipment a = new Shipment("a", "Ann");
    a.road = "RD-1";
    Shipment b = new Shipment("b", "Bo");
    b.road = "RD-1";
    assertEquals(Map.of("RD-1", 2), Audit.countByDepot(List.of(a, b, new Shipment("c", "Cy"))));
  }
}