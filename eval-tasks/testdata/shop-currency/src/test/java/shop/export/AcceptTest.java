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
  private static final List<Order> ORDERS =
      List.of(
          new Order(
              "A1", "Ann", "retail", "", "Springfield", "", Status.PAID,
              List.of(new Line("pen", 2, 150))),
          new Order(
              "B2", "Bob", "wholesale", "", "Shelbyville", "", Status.SHIPPED,
              List.of(new Line("ink", 12, 500))));

  private static final Currency EUR = new Currency("EUR", "€", 2);
  private static final Currency JPY = new Currency("JPY", "¥", 0);
  private static final Currency KWD = new Currency("KWD", "KD", 3);

  @Test
  void csvCurrency() {
    assertEquals(
        "id,customer,city,total\nA1,Ann,Springfield,3.60\nB2,Bob,Shelbyville,47.04\n",
        Csv.write(ORDERS, EUR));
    assertEquals(
        "id,customer,city,total\nA1,Ann,Springfield,360\nB2,Bob,Shelbyville,4704\n",
        Csv.write(ORDERS, JPY));
    assertEquals(
        "id,customer,city,total\nA1,Ann,Springfield,0.360\nB2,Bob,Shelbyville,4.704\n",
        Csv.write(ORDERS, KWD));
  }

  @Test
  void jsonCurrency() {
    List<String> totals = new ArrayList<>();
    Matcher m = Pattern.compile("\"total\": \"([^\"]*)\"").matcher(Json.write(ORDERS, JPY));
    while (m.find()) {
      totals.add(m.group(1));
    }
    assertEquals(List.of("360", "4704"), totals);
  }

  @Test
  void markdownCurrency() {
    assertEquals(
        "| Order | Customer | Total |\n|---|---|---|\n| A1 | Ann | €3.60 |\n"
            + "| B2 | Bob | €47.04 |\n\nTotal: €50.64\n",
        Markdown.write(ORDERS, EUR));
    assertEquals(
        "| Order | Customer | Total |\n|---|---|---|\n| A1 | Ann | ¥360 |\n"
            + "| B2 | Bob | ¥4704 |\n\nTotal: ¥5064\n",
        Markdown.write(ORDERS, JPY));
  }
}
