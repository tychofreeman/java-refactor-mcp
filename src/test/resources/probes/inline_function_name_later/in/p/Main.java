package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    int r = twice(next());
    int x = 40;
    System.out.println(r + x + " " + n);
  }
}
