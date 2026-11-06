package shop.pricing;

import shop.order.Line;
import shop.order.Order;

/** What an order costs. */
public final class Pricing {
  private Pricing() {}

  /** What the customer pays for o, in cents: the lines' prices after discounts, plus tax. */
  public static int total(Order o) {
    int subtotal = 0;
    for (Line l : o.lines()) {
      int price = l.unitCents();
      if (o.customerKind().equals("wholesale")) {
        price = price * 80 / 100;
        if (l.qty() >= 100) {
          price = price * 95 / 100;
        } else if (l.qty() >= 10) {
          price = price * 98 / 100;
        }
      } else if (o.customerKind().equals("staff")) {
        price = price * 70 / 100;
      } else {
        if (l.qty() >= 10) {
          if (l.unitCents() > 1000) {
            price = price * 90 / 100;
          } else {
            price = price * 95 / 100;
          }
        }
      }
      subtotal += price * l.qty();
    }
    int tax = 0;
    if (o.customerKind().equals("wholesale")) {
      tax = 0;
    } else if (o.customerKind().equals("staff")) {
      tax = subtotal * 10 / 100;
    } else {
      tax = subtotal * 20 / 100;
      if (subtotal > 100000) {
        tax = subtotal * 18 / 100;
      }
    }
    return subtotal + tax;
  }
}
