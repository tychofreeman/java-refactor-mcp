package freight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class TailAcceptTest {
  @Test
  void tailKeepsTheLastFourCharacters() throws Exception {
    Truck truck = new Truck();
    assertEquals("12", truck.tail("12"));
    assertEquals("0042", truck.tail("RD-NORTH-0042"));
    Method tail = Truck.class.getDeclaredMethod("tail", String.class);
    assertFalse(Modifier.isStatic(tail.getModifiers()), "tail is an instance method");
    assertThrows(NoSuchMethodException.class, () -> Truck.class.getDeclaredMethod("last", String.class, int.class));
  }

  @Test
  void truckReturnsWhatItDid() {
    Truck truck = new Truck();
    Shipment s = new Shipment("s-000017", "acme");
    s.road = "RD-0004";
    assertEquals("truck 0004 R", truck.label(s, new Order("R", 1)));
    Shipment one = new Shipment("s2", "Bo");
    one.road = "1";
    assertEquals(List.of("0004", "1"), truck.tails(List.of(s, one)));
    assertEquals("ACME", truck.badge(s));
    assertEquals("TR0345", truck.plate(120345));
    truck.ship(s, new Order("ORD-1", 3));
    assertEquals(List.of("0004: ORD-1"), truck.shipped);
  }
}