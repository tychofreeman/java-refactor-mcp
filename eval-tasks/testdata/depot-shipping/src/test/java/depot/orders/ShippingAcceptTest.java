package depot.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import depot.stock.Sku;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShippingAcceptTest {
  static Order order(Region region, int grams) {
    Customer ada = new Customer("Ada", new Account("AC-7", new Address("x", "y", "z")), region);
    return new Order("O-3", ada, List.of(new Line(new Sku("N-1"), 1, 1000, grams)));
  }

  @Test
  void shippingGoesToTheCustomersRegion() {
    assertEquals(400, Shipping.cost(order(Region.DOMESTIC, 1000)));
    assertEquals(5400, Shipping.cost(order(Region.WORLD, 2500)));
  }

  @Test
  void quotesAndDispatchAreUnchanged() {
    assertEquals("O-3: 28.00", new Quotes().quote(order(Region.EU, 1001)));
    assertEquals(List.of("O-3"), new Dispatch().costly(List.of(order(Region.EU, 1001)), 1700));
  }
}
