package shop.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import shop.order.Address;
import shop.order.Order;
import shop.order.Status;

class AcceptTest {
  private static final Address US = new Address("1 Main St", "Springfield", "12345", "US");
  private static final Address GB = new Address("10 Downing St", "London", "SW1A 2AA", "GB");

  @Test
  void validAddressCountry() {
    assertNull(Shipping.validAddress(US), "a valid US address");
    assertNull(Shipping.validAddress(GB), "a valid GB address");
    String noCountry = Shipping.validAddress(new Address("1 Main St", "Springfield", "12345", ""));
    assertTrue(noCountry != null && noCountry.contains("country"), "no country: " + noCountry);
    assertNotNull(
        Shipping.validAddress(new Address("1 Main St", "Springfield", "1234", "US")),
        "a US postcode of 4 digits");
  }

  @Test
  void labelCountry() {
    assertEquals(
        "Ann\n1 Main St\n12345 Springfield\n",
        Shipping.ship(new Order("A1", "Ann", "retail", US, Status.PAID, List.of())));
    assertEquals(
        "Bea\n10 Downing St\nSW1A 2AA London\nGB\n",
        Shipping.ship(new Order("B2", "Bea", "retail", GB, Status.PAID, List.of())));
  }
}
