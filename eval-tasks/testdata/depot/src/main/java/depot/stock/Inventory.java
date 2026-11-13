package depot.stock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The depot's stock of every SKU, in the order the SKUs were first put. */
public final class Inventory {
  private final Catalog catalog;
  private final Map<Sku, Stock> stock = new LinkedHashMap<>();

  public Inventory(Catalog catalog) {
    this.catalog = catalog;
  }

  public void put(Sku sku, Stock s) {
    stock.put(sku, s);
  }

  public Stock stockOf(Sku sku) {
    return stock.get(sku);
  }

  /** The units on hand across every SKU. */
  public int unitsOnHand() {
    int units = 0;
    for (Stock s : stock.values()) {
      units += s.onHand();
    }
    return units;
  }

  /** What to order from suppliers today. */
  public List<ReorderLine> reorders() {
    List<ReorderLine> lines = new ArrayList<>();
    return lines;
  }
}
