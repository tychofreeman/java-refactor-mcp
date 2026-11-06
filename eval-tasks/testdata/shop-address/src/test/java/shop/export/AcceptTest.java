package shop.export;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import shop.order.Address;
import shop.order.Line;
import shop.order.Order;
import shop.order.Status;

class AcceptTest {
  @Test
  void exportsShowTheCity() {
    List<Order> orders =
        List.of(
            new Order(
                "A1", "Ann", "retail", new Address("", "Springfield", "", "US"), Status.PAID,
                List.of(new Line("pen", 2, 150))));
    String csv = Csv.write(orders);
    assertTrue(csv.contains("A1,Ann,Springfield,3.60"), csv);
  }
}
