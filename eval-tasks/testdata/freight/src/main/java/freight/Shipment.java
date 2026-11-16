package freight;

/** Something to send, with where each carrier picks it up. */
public class Shipment {
  public String id;
  public String sender;
  public String road; // a road depot code
  public String rail; // a rail yard code
  public String air; // an airport code

  public Shipment(String id, String sender) {
    this.id = id;
    this.sender = sender;
  }

  /** A copy of this shipment under another id, picked up at the same places. */
  public Shipment copy(String newId) {
    Shipment s = new Shipment(newId, sender);
    s.road = road;
    s.rail = rail;
    s.air = air;
    return s;
  }
}