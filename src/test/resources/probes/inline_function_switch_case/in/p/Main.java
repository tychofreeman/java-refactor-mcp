package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    int k = 2;
    int r = switch (k) {
      case 1 -> twice(next());
      default -> 0;
    };
    System.out.println(r + " " + n);
  }
}
