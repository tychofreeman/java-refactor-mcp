package depot.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import depot.stock.Sku;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdersTest {
  static final Address HOME = new Address("1 Quay St", "Cork", "IE");

  static Order order(String id, Region region, int grams) {
    Customer ada = new Customer("Ada", new Account("AC-7", HOME), region);
    return new Order(id, ada, List.of(new Line(new Sku("N-1"), 2, 1250, grams)));
  }

  @Test
  void shippingIsChargedPerStartedKilo() {
    Order order = order("O-1", Region.EU, 700);
    assertEquals(1800, Shipping.cost(order, order.customer().region()));
  }

  @Test
  void quotesTheTotalWithShipping() {
    assertEquals("O-1: 43.00", new Quotes().quote(order("O-1", Region.EU, 700)));
  }

  @Test
  void findsCostlyOrders() {
    List<Order> orders =
        List.of(order("O-1", Region.EU, 700), order("O-2", Region.DOMESTIC, 300));
    assertEquals(List.of("O-1"), new Dispatch().costly(orders, 1500));
  }
}
