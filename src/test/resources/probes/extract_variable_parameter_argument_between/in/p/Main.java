package p;
public class Main {
  static int step;
  static int run(int q, int r) {
    return (q * 3 + 1) * 10 + r;
  }
  static int tick() { return ++step; }
  public static void main(String[] args) {
    System.out.println(run(step, tick()));
  }
}
