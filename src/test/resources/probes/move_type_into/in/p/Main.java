package p;

public class Main {
  static int next() {
    return 1;
  }

  static class Counter {
    static final String LABEL = "count";
    int n = next();

    int step() {
      n += next();
      return n;
    }
  }

  public static void main(String[] args) {
    Counter c = new Counter();
    System.out.println(Counter.LABEL + " " + c.step() + " " + Other.next());
  }
}
