package shop.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import shop.order.Line;
import shop.order.Order;
import shop.order.Status;

class PricingTest {
  private static int total(String kind, int qty, int unitCents) {
    return Pricing.total(
        new Order("", "", kind, "", "", "", Status.PAID, List.of(new Line("", qty, unitCents))));
  }

  @Test
  void total() {
    assertEquals(360, total("retail", 2, 150), "retail");
    assertEquals(5700, total("retail", 10, 500), "retail, 10 cheap");
    assertEquals(21600, total("retail", 10, 2000), "retail, 10 dear");
    assertEquals(212400, total("retail", 100, 2000), "retail, lower tax over 1000");
    assertEquals(1600, total("wholesale", 2, 1000), "wholesale");
    assertEquals(4704, total("wholesale", 12, 500), "wholesale, 10");
    assertEquals(76000, total("wholesale", 100, 1000), "wholesale, 100");
    assertEquals(2310, total("staff", 3, 1000), "staff");
    assertEquals(7700, total("staff", 10, 1000), "staff, 10");
  }
}
