package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    boolean c = false;
    boolean r = c && twice(next()) > 0;
    System.out.println(r + " " + n);
  }
}
