package p;
public class Main {
  static int n;
  public String toString() { return "called " + ++n; }
  static int f(int x, String unused) { return x; }
  public static void main(String[] args) {
    Main m = new Main();
    System.out.println(f(1, "" + m) + " " + n);
  }
}
