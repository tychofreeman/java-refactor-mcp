package p;
public class Main {
  static final int K = 3;
  static int n;
  static int next() { return ++n; }
  static int area(int a, int b, long c) {
    a++;
    return (int) (c * 2 + a + b);
  }
  public static void main(String[] args) {
    int x = next();
    int y = next() + 5;
    System.out.println(area(x, y, x * y + K));
    System.out.println(area(x + 1, y, (x + 1) * y + Main.K));
    System.out.println(area(y - 1, x + 1, (y - 1) * (x + 1) + K));
    System.out.println(n);
  }
}
