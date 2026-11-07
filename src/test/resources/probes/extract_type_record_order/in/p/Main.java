package p;
public class Main {
  static String log = "";
  static String s(String v) { log += v; return v; }
  public static void main(String[] args) {
    Order a = new Order(s("1"), s("Main"), s("Town"), 5);
    Order b = new Order("1", "Main", "Town", 5);
    System.out.println(log + " " + a.street() + " " + a.city() + " " + a.label());
    System.out.println(a.equals(b) + " " + (a.hashCode() == b.hashCode()));
  }
}
