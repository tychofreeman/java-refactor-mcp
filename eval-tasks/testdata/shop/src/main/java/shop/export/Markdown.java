package shop.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import shop.order.Order;
import shop.order.Status;
import shop.pricing.Pricing;

/** Orders written out as a Markdown table, for people. */
public final class Markdown {
  private Markdown() {}

  /** The orders that are not pending as a table, by ID, with the grand total under it. */
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
    sb.append("| Order | Customer | Total |\n|---|---|---|\n");
    int sum = 0;
    for (Order o : list) {
      int t = Pricing.total(o);
      sum += t;
      sb.append(String.format("| %s | %s | $%d.%02d |\n", o.id(), o.customer(), t / 100, t % 100));
    }
    sb.append(String.format("\nTotal: $%d.%02d\n", sum / 100, sum % 100));
    return sb.toString();
  }
}
