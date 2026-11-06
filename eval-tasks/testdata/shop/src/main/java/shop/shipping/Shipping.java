package shop.shipping;

import java.util.ArrayList;
import java.util.List;
import shop.order.Order;

/** Checks addresses and prints labels. */
public final class Shipping {
  private Shipping() {}

  /** What is wrong with an address, or null when nothing is. */
  public static String validAddress(String street, String city, String postcode) {
    List<String> missing = new ArrayList<>();
    if (street.isBlank()) {
      missing.add("street");
    }
    if (city.isBlank()) {
      missing.add("city");
    }
    if (postcode.isBlank()) {
      missing.add("postcode");
    }
    if (!missing.isEmpty()) {
      return "address is missing " + String.join(", ", missing);
    }
    if (!postcode.matches("[0-9]{5}")) {
      return "postcode \"" + postcode + "\" must have 5 digits";
    }
    return null;
  }

  /** The shipping label for o. */
  public static String label(Order o) {
    return o.customer() + "\n" + o.street() + "\n" + o.postcode() + " " + o.city() + "\n";
  }

  /** o's label, after checking its address; refused, naming the order, when it is not valid. */
  public static String ship(Order o) {
    String problem = validAddress(o.street(), o.city(), o.postcode());
    if (problem != null) {
      throw new IllegalArgumentException("order " + o.id() + ": " + problem);
    }
    return label(o);
  }
}
