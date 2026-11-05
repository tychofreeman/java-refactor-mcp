package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    int r = next() * 10 + twice(next());
    System.out.println(r + " " + n);
  }
}
