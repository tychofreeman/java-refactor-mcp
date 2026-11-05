package p;
public class Main {
  static int n;
  static int next() { return ++n; }
  static int twice(int x) { return x + x; }
  public static void main(String[] args) {
    int s = 0;
    for (int i = 0; i < 3; i = twice(next())) {
      s += i;
    }
    System.out.println(s + " " + n);
  }
}
