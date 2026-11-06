package shop.export;

import java.util.List;
import shop.order.Line;
import shop.order.Order;
import shop.order.Status;

/** A mix of customers and statuses. */
final class Sample {
  private Sample() {}

  static final List<Order> ORDERS =
      List.of(
          new Order(
              "D4", "Di", "retail", "", "Ogdenville", "", Status.CANCELLED,
              List.of(new Line("pad", 1, 1000))),
          new Order(
              "A1", "Ann", "retail", "", "Springfield", "", Status.PAID,
              List.of(new Line("pen", 2, 150))),
          new Order(
              "C3", "Cy", "staff", "", "Springfield", "", Status.PENDING,
              List.of(new Line("pen", 1, 150))),
          new Order(
              "B2", "Bob", "wholesale", "", "Shelbyville", "", Status.SHIPPED,
              List.of(new Line("ink", 12, 500))));
}
