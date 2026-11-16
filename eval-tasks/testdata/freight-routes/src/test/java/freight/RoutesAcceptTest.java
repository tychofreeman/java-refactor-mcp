package freight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audit.Audit;
import depots.Depots;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import manifests.Manifest;
import org.junit.jupiter.api.Test;

class RoutesAcceptTest {
  private static Shipment shipment(String id, String sender, Map<String, String> routes) {
    Shipment s = new Shipment(id, sender);
    s.routes.putAll(routes);
    return s;
  }

  @Test
  void aNewShipmentHasNoRoutes() {
    assertTrue(new Shipment("s1", "Ann").routes.isEmpty());
    assertThrows(NoSuchFieldException.class, () -> Shipment.class.getDeclaredField("road"));
    assertThrows(NoSuchFieldException.class, () -> Shipment.class.getDeclaredField("rail"));
  }

  @Test
  void carriersReadTheRoutes() {
    Truck truck = new Truck();
    Train train = new Train();
    Shipment s = shipment("s1", "Ann", Map.of("road", "RD-NORTH-0042", "rail", "YARD7"));
    List<String> lines =
        new Dispatcher(List.of(truck, train)).dispatch(List.of(s), new Order("ORD-1", 25));
    assertEquals(List.of("Ann: truck,train"), lines);
    assertEquals(List.of("0042: ORD-1"), truck.shipped);
    assertEquals(List.of("YARD7: ORD-1 25kg"), train.shipped);
    assertEquals("truck 0042 R", truck.label(s, new Order("R", 1)));
    assertEquals(s.routes, s.copy("s2").routes);
  }

  @Test
  void theOtherPackagesReadAndWriteTheRoutes() {
    Shipment s = shipment("s1", "Ann", Map.of("road", "RD-1"));
    assertEquals("s1 RD-1/-", Manifest.line(s));
    Manifest m = new Manifest();
    m.add(s);
    assertEquals(1, m.reroute("RD-1", "RD-9"));
    assertEquals(Map.of("road", "RD-9"), s.routes);
    new Depots(Map.of("north", "RD-N"), Map.of("north", "YARD-N")).assign(s, "north");
    assertEquals(Map.of("road", "RD-9", "rail", "YARD-N"), new HashMap<>(s.routes));
    assertEquals("s1 road=RD-9 rail=YARD-N air=null", Audit.describe(s));
    assertEquals(List.of(), Audit.problems(s));
  }
}