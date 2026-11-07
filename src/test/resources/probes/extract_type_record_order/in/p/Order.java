package p;
public record Order(String id, String street, String city, int total) {
  String label() { return id + ": " + street + ", " + city(); }
}
