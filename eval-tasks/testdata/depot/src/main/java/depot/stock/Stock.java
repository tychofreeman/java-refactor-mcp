package depot.stock;

/** How much of one SKU the depot holds, and when to order more. */
public final class Stock {
  private final int onHand;
  private final int reserved;
  private final int reorderPoint;
  private final int reorderQuantity;

  public Stock(int onHand, int reserved, int reorderPoint, int reorderQuantity) {
    this.onHand = onHand;
    this.reserved = reserved;
    this.reorderPoint = reorderPoint;
    this.reorderQuantity = reorderQuantity;
  }

  /** Units on the shelves. */
  public int onHand() {
    return onHand;
  }

  /** Units on the shelves that are not promised to an order. */
  public int available() {
    return onHand - reserved;
  }

  /** Below this many available units, the SKU is reordered. */
  public int reorderPoint() {
    return reorderPoint;
  }

  /** How many units one reorder asks for. */
  public int reorderQuantity() {
    return reorderQuantity;
  }
}
