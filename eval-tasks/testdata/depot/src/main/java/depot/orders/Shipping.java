package depot.orders;

/** What shipping costs. */
public final class Shipping {
  private Shipping() {}

  /** Shipping for the order to the region, in cents: the region's rate per started kilogram. */
  public static int cost(Order order, Region region) {
    int kilos = (order.weightGrams() + 999) / 1000;
    return kilos * region.centsPerKilo();
  }
}
