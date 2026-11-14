package depot.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReordersAcceptTest {
  @Test
  void reordersEverySkuBelowItsReorderPoint() {
    Supplier acme = new Supplier("Acme", 3);
    Supplier bolt = new Supplier("Bolt & Co", 5);
    Sku nails = new Sku("N-1");
    Sku screws = new Sku("S-2");
    Sku glue = new Sku("G-3");
    Sku tape = new Sku("T-4");
    Catalog catalog = new Catalog();
    catalog.supply(nails, acme);
    catalog.supply(screws, bolt);
    catalog.supply(glue, acme);
    catalog.supply(tape, bolt);
    Inventory inventory = new Inventory(catalog);
    inventory.put(nails, new Stock(10, 6, 5, 100));
    inventory.put(screws, new Stock(50, 0, 20, 200));
    inventory.put(glue, new Stock(8, 0, 8, 12));
    inventory.put(tape, new Stock(3, 1, 4, 30));
    assertEquals(
        List.of(new ReorderLine(nails, acme, 100), new ReorderLine(tape, bolt, 30)),
        inventory.reorders());
  }

  @Test
  void reordersNothingWhenAllIsStocked() {
    Inventory inventory = new Inventory(new Catalog());
    inventory.put(new Sku("S-2"), new Stock(50, 0, 20, 200));
    assertEquals(List.of(), inventory.reorders());
  }
}
