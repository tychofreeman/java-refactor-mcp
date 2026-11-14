package depot.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryTest {
  @Test
  void countsUnitsOnHand() {
    Inventory inventory = new Inventory(new Catalog());
    inventory.put(new Sku("N-1"), new Stock(10, 6, 5, 100));
    inventory.put(new Sku("S-2"), new Stock(50, 0, 20, 200));
    assertEquals(60, inventory.unitsOnHand());
    assertEquals(4, inventory.stockOf(new Sku("N-1")).available());
  }
}
