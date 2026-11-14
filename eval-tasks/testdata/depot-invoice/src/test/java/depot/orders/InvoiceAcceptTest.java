package depot.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import depot.stock.Sku;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceAcceptTest {
  @Test
  void billsTheCustomersAccountForTheTotalWithShipping() {
    Address billing = new Address("9 Mill Rd", "Leeds", "UK");
    Customer ada = new Customer("Ada", new Account("AC-7", billing), Region.WORLD);
    Order order =
        new Order(
            "O-9",
            ada,
            List.of(new Line(new Sku("N-1"), 2, 1250, 300), new Line(new Sku("G-3"), 1, 499, 800)));
    assertEquals(new Invoice("O-9", "AC-7", billing, 2999 + 3600), new Invoicing().invoiceFor(order));
  }
}
