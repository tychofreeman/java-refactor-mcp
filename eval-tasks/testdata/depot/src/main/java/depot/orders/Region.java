package depot.orders;

/** Where an order ships to, and what shipping there costs. */
public enum Region {
  DOMESTIC(400),
  EU(900),
  WORLD(1800);

  private final int centsPerKilo;

  Region(int centsPerKilo) {
    this.centsPerKilo = centsPerKilo;
  }

  /** Shipping for each started kilogram, in cents. */
  public int centsPerKilo() {
    return centsPerKilo;
  }
}
