package p;
public class Main {
  static String log = "";
  static Counter make() { log += "m"; return new Counter(); }
  static int next() { log += "n"; return 1; }
  public static void main(String[] args) {
    int r = Counter.add(make(), next());
    System.out.println(r + " " + log);
  }
}
class Counter {
  int base = 2;
  static int add(Counter c, int k) { return c.base + k; }
}
