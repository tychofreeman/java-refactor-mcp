package warehouse.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AcceptTest {

  @Test
  void renamed() {
    assertEquals(0.25, Pricing.markup);
  }

  @Test
  void prices() {
    assertEquals(28, Pricing.bulk(10, 2, 3), "bulk");
    assertEquals(128, Pricing.quote(100, 3), "quote");
    assertEquals(120, Pricing.reduced(100, 0.05), "reduced");
    assertEquals(100, Pricing.reduced(100, 0.5), "reduced below zero");
    assertEquals(30, Pricing.newRate("acme", 2, 5).storage(10), "storage");
    assertEquals(10, Pricing.floor(40), "floor");
  }
}
