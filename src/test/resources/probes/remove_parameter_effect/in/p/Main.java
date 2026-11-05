package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int f(int x, int unused) { return x; }
  public static void main(String[] args) {
    System.out.println(f(1, next()) + " " + n);
  }
}
