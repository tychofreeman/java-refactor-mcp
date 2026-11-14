package p;
public class Main {
  static int ticks;
  static int tick() { return ++ticks; }
  static int run(int a, int t, int b) {
    return (a + b) * 100 + t;
  }
  public static void main(String[] args) {
    int x = 1;
    int y = 2;
    System.out.println(run(x, tick(), y));
  }
}
