package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int a, int b) { return a + b; }
  public static void main(String[] args) {
    System.out.println(twice(next(), next() * 2));
  }
}
