package shop.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import shop.order.Order;
import shop.order.Status;
import shop.pricing.Pricing;

/** Orders written out as JSON, for other systems. */
public final class Json {
  private Json() {}

  /** The orders that are not pending as a JSON array, by ID. */
  public static String write(List<Order> orders) {
    List<Order> list = new ArrayList<>();
    for (Order o : orders) {
      if (o.status() != Status.PENDING) {
        list.add(o);
      }
    }
    list.sort(Comparator.comparing(Order::id));
    List<String> rows = new ArrayList<>();
    for (Order o : list) {
      int t = Pricing.total(o);
      rows.add(
          "  {\n"
              + "    \"id\": "
              + quote(o.id())
              + ",\n    \"customer\": "
              + quote(o.customer())
              + ",\n    \"city\": "
              + quote(o.city())
              + ",\n    \"total\": "
              + quote(String.format("%d.%02d", t / 100, t % 100))
              + "\n  }");
    }
    return rows.isEmpty() ? "[]\n" : "[\n" + String.join(",\n", rows) + "\n]\n";
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
