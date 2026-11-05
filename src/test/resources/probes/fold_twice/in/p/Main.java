package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] a) {
    int r = next() + next();
    System.out.println(r + " " + n);
  }
}
