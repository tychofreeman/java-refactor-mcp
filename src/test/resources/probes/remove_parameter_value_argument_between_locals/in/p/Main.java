package p;
public class Main {
  static int ticks;
  static int tick() { return ++ticks; }
  static int sum(int a, int t, int b, int c) { return a * 1000 + t * 100 + b * 10 + c; }
  public static void main(String[] args) {
    int x = 1;
    int y = 2;
    System.out.println(sum(x, tick(), y, x + y));
  }
}
