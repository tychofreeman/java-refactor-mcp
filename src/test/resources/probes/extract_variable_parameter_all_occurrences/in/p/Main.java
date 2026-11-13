package p;
public class Main {
  static int step;
  static int run(int q, int r) {
    int x = q * 3 + 1;
    for (int i = 0; i < r; i++) {
      x += q * 3 + 1;
    }
    return x * (q * 3 + 1);
  }
  static int tick() { return ++step; }
  public static void main(String[] args) {
    int[] v = {2};
    tick();
    System.out.println(run(v[0], 3) + " " + run(4, 0) + " " + run(-1, 2));
  }
}
