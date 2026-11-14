package p;
class Keys {
  static final String PREFIX = "k:";
  static String declared(String name) { return PREFIX + name; }
  static String erased(String name) { return Keys.declared(name) + Keys.PREFIX; }
}
public class Main {
  static final String PREFIX = "main:";
  static String declared(String name) { return "main " + name; }
  public static void main(String[] args) {
    System.out.println(Keys.erased("a"));
  }
}
