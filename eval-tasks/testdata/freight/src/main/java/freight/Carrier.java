package freight;

/** Ships an order to a shipment's pickup point by one means. */
public interface Carrier {
  void ship(Shipment s, Order o);

  String name();
}