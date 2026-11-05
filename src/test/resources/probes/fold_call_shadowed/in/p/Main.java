package p;
public class Main {
  static int twice(int x) { return x * 2; }
  static class Inner {
    static int twice(int x) { return -1; }
    static int f(int v) { return v * 2; }
  }
  public static void main(String[] args) {
    System.out.println(Inner.f(3));
  }
}
