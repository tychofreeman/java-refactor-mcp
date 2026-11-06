package shop.order;

/** One item of an order: unitCents is the price of one unit, before discounts and tax. */
public record Line(String sku, int qty, int unitCents) {}
