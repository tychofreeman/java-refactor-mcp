package depot.stock;

import java.util.HashMap;
import java.util.Map;

/** Who supplies each SKU. */
public final class Catalog {
  private final Map<Sku, Supplier> suppliers = new HashMap<>();

  public void supply(Sku sku, Supplier supplier) {
    suppliers.put(sku, supplier);
  }

  /** The SKU's supplier; every SKU the depot stocks has one. */
  public Supplier supplierOf(Sku sku) {
    Supplier supplier = suppliers.get(sku);
    if (supplier == null) {
      throw new IllegalArgumentException("no supplier for " + sku.code());
    }
    return supplier;
  }
}
