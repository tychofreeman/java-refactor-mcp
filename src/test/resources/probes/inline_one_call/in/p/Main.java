package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    int a = twice(next());
    int b = twice(next());
    System.out.println(a + " " + b + " " + n);
  }
}
