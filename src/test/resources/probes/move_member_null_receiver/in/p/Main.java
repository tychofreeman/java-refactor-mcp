package p;
public class Main {
  static int n;
  static int next() { n++; return 5; }
  public static void main(String[] args) {
    Counter c = null;
    try {
      c.add(next());
    } catch (NullPointerException e) {
      System.out.println("npe " + n);
    }
  }
}
class Counter {
  int base = 3;
  int add(int k) { return base + k; }
}
class Ops {
}
