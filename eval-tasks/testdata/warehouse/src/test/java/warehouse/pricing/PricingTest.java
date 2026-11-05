package warehouse.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PricingTest {

  @Test
  void prices() {
    assertEquals(125, Pricing.price(100));
    assertEquals(128, Pricing.quote(100, 3));
    assertEquals(30, Pricing.newRate("acme", 2, 5).storage(10));
    assertEquals(1, Pricing.floor(2));
  }
}
