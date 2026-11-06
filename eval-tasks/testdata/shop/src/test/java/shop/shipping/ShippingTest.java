package shop.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import shop.order.Order;
import shop.order.Status;

class ShippingTest {
  @Test
  void validAddress() {
    assertNull(Shipping.validAddress("1 Main St", "Springfield", "12345"));
    assertTrue(Shipping.validAddress("", "Springfield", "12345").contains("missing street"));
    assertTrue(Shipping.validAddress("1 Main St", "", "").contains("missing city, postcode"));
    assertTrue(Shipping.validAddress("1 Main St", "Springfield", "1234").contains("5 digits"));
    assertTrue(Shipping.validAddress("1 Main St", "Springfield", "1234a").contains("5 digits"));
  }

  @Test
  void ship() {
    Order o =
        new Order("A1", "Ann", "retail", "1 Main St", "Springfield", "12345", Status.PAID, List.of());
    assertEquals("Ann\n1 Main St\n12345 Springfield\n", Shipping.ship(o));
    Order bad =
        new Order("A1", "Ann", "retail", "1 Main St", "Springfield", "", Status.PAID, List.of());
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Shipping.ship(bad));
    assertTrue(e.getMessage().contains("order A1"), e.getMessage());
  }
}
