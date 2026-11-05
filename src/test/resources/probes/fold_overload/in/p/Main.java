package p;
public class Main {
  static String describe(Object o) { return "object"; }
  static String describe(String s) { return "string"; }
  static String show(Object o) { return "[" + o + "]"; }
  static String show(String s) { return "<" + s + ">"; }
  public static void main(String[] args) {
    String s = "x";
    System.out.println("[" + s + "]");
  }
}
