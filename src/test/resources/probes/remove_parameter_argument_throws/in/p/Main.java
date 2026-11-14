package p;
public class Main {
  static int f(int x, int unused) { return x; }
  public static void main(String[] args) {
    int zero = args.length;
    try {
      System.out.println(f(1, 1 / zero));
    } catch (ArithmeticException e) {
      System.out.println("caught");
    }
  }
}
