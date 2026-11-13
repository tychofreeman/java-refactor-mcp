package p;

class Main {
  static int step;

  static int run(int q, int k, int r) {
    return (k) * 10 + r;
  }

  static int tick() {
    return ++step;
  }

  int use() {
    return run(step, step * 3 + 1, tick()) + run(2, 2 * 3 + 1, 3);
  }
}
