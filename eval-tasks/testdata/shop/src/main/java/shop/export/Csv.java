package shop.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import shop.order.Order;
import shop.order.Status;
import shop.pricing.Pricing;

/** Orders written out as CSV, for other systems. */
public final class Csv {
  private Csv() {}

  /** The orders that are not pending, one per line, by ID. */
  public static String write(List<Order> orders) {
    List<Order> list = new ArrayList<>();
    for (Order o : orders) {
      if (o.status() == Status.PENDING) {
        continue;
      }
      list.add(o);
    }
    list.sort(Comparator.comparing(Order::id));
    StringBuilder sb = new StringBuilder();
    sb.append("id,customer,city,total\n");
    for (Order o : list) {
      int total = Pricing.total(o);
      sb.append(
          String.format(
              "%s,%s,%s,%d.%02d\n", o.id(), o.customer(), o.city(), total / 100, total % 100));
    }
    return sb.toString();
  }
}
