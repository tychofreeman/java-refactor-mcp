package p;
public class Main {
  static int base = 1;
  static int bump() { base += 10; return base; }
  static int f(int v, int k, int c) { return v * 1000 + k * 10 + c; }
  public static void main(String[] args) {
    int x = 3;
    System.out.println(f(x + base, bump(), x));
  }
}
