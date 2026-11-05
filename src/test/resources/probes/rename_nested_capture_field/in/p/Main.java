package p;
public class Main {
  static String a = "outer";
  static class Inner {
    static String b = "inner";
    static String run() { return a; }
  }
  public static void main(String[] args) {
    System.out.println(Inner.run());
  }
}
