package p;
public class Main {
  static int x = 10;
  static int g() { x = 100; return 1; }
  static int diff(int a, int b) { return b - a; }
  public static void main(String[] args) {
    int r = x - g();
    System.out.println(r + " " + x);
  }
}
