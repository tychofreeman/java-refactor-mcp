package freight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FreightTest {
  private static Shipment shipment(String id, String sender, String road, String rail) {
    Shipment s = new Shipment(id, sender);
    s.road = road;
    s.rail = rail;
    return s;
  }

  @Test
  void dispatchShipsThroughEveryCarrier() {
    Truck truck = new Truck();
    Train train = new Train();
    Shipment s = shipment("s1", "Ann", "RD-NORTH-0042", "YARD7");
    List<String> lines =
        new Dispatcher(List.of(truck, train)).dispatch(List.of(s), new Order("ORD-1", 25));
    assertEquals(List.of("Ann: truck,train"), lines);
    assertEquals(List.of("0042: ORD-1"), truck.shipped);
    assertEquals(List.of("YARD7: ORD-1 25kg"), train.shipped);
  }

  @Test
  void truckShowsTheLastFourCharacters() {
    Truck truck = new Truck();
    Shipment s = shipment("s-000017", "acme", "RD-0004", null);
    assertEquals("truck 0004 R", truck.label(s, new Order("R", 1)));
    assertEquals("****0004", truck.masked("RD-0004"));
    assertEquals("T-0017", truck.shortForm(s));
    assertEquals("ACME", truck.badge(s));
    assertEquals("0042", truck.hint(42));
    assertEquals("TR7", truck.plate(7));
    assertEquals(List.of("0004"), truck.codes(List.of(s, new Shipment("s2", "Bo"))));
    assertTrue(truck.same(s, shipment("s3", "Cy", "RD-WEST-0004", null)));
  }

  @Test
  void aCopyIsPickedUpAtTheSamePlaces() {
    Shipment copy = shipment("s1", "Ann", "RD-1", "YARD1").copy("s2");
    assertEquals("RD-1", copy.road);
    assertEquals("YARD1", copy.rail);
    assertEquals("s2", copy.id);
  }

  @Test
  void aHeavyOrderNeedsAHeavyYard() {
    Train train = new Train();
    assertFalse(train.takes(shipment("s1", "Ann", null, "YARD7"), new Order("O", 900)));
    assertTrue(train.takes(shipment("s1", "Ann", null, "YARD7H"), new Order("O", 900)));
  }
}