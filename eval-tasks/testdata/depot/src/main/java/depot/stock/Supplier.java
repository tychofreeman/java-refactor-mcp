package depot.stock;

/** A company the depot buys from, and the days it takes to deliver. */
public record Supplier(String name, int leadDays) {}
