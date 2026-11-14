package depot.orders;

import depot.stock.Sku;

/** Units of one SKU in an order, at a unit price and weight. */
public record Line(Sku sku, int quantity, int unitCents, int unitGrams) {
  public int cents() {
    return quantity * unitCents;
  }

  public int grams() {
    return quantity * unitGrams;
  }
}
