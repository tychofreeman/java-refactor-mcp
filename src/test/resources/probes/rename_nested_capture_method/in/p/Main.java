package p;
public class Main {
  static String helper(int x) { return "outer" + x; }
  static class Inner {
    static String show(int x) { return "inner" + x; }
    static String run() { return helper(1); }
  }
  public static void main(String[] args) {
    System.out.println(Inner.run());
  }
}
