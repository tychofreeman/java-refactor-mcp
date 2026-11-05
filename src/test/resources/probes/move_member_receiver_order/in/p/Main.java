package p;
public class Main {
  static int n;
  static Counter make() { n = n * 10 + 1; return new Counter(); }
  static int next() { n = n * 10 + 2; return 5; }
  public static void main(String[] args) {
    int r = make().add(next());
    System.out.println(r + " " + n);
  }
}
class Counter {
  int base = 3;
  int add(int k) { return base + k; }
}
class Ops {
}
