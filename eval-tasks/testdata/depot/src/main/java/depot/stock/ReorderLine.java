package depot.stock;

/** One order to a supplier: how many units of a SKU. */
public record ReorderLine(Sku sku, Supplier supplier, int quantity) {}
