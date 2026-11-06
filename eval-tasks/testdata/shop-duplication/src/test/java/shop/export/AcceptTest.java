package shop.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import shop.order.Line;
import shop.order.Order;
import shop.order.Status;

class AcceptTest {
  private static Order order(String id, String customer, Status status, int unitCents) {
    return new Order(
        id, customer, "retail", "", "X", "", status, List.of(new Line("a", 1, unitCents)));
  }

  private static final List<Order> ORDERS =
      List.of(
          order("Z9", "Ann", Status.PAID, 1000),
          order("A1", "Bob", Status.PAID, 500),
          order("C1", "Al", Status.CANCELLED, 700),
          order("B1", "Ann", Status.SHIPPED, 100),
          order("D1", "Aaron", Status.PENDING, 900));

  @Test
  void csvLeavesOutCancelledAndSortsByCustomer() {
    assertEquals(
        "id,customer,city,total\nB1,Ann,X,1.20\nZ9,Ann,X,12.00\nA1,Bob,X,6.00\n",
        Csv.write(ORDERS));
  }

  @Test
  void jsonLeavesOutCancelledAndSortsByCustomer() {
    List<String> ids = new ArrayList<>();
    Matcher m = Pattern.compile("\"id\": \"([^\"]*)\"").matcher(Json.write(ORDERS));
    while (m.find()) {
      ids.add(m.group(1));
    }
    assertEquals(List.of("B1", "Z9", "A1"), ids);
  }

  @Test
  void markdownLeavesOutCancelledAndSortsByCustomer() {
    assertEquals(
        "| Order | Customer | Total |\n|---|---|---|\n| B1 | Ann | $1.20 |\n| Z9 | Ann | $12.00 |\n"
            + "| A1 | Bob | $6.00 |\n\nTotal: $19.20\n",
        Markdown.write(ORDERS));
  }
}
