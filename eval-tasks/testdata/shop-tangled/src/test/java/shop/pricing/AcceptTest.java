package shop.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import shop.order.Line;
import shop.order.Order;
import shop.order.Status;

class AcceptTest {
  private static int partner(int qty, int unitCents) {
    return Pricing.total(
        new Order(
            "", "", "partner", "", "", "", Status.PAID, List.of(new Line("", qty, unitCents))));
  }

  @Test
  void partner() {
    assertEquals(1920, partner(2, 1000), "wholesale price, retail tax");
    assertEquals(5760, partner(12, 500), "no quantity discount");
    assertEquals(188800, partner(200, 1000), "retail's lower tax over 1000");
  }
}
