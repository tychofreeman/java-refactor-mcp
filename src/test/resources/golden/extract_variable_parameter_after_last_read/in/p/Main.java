package p;

class Main {
  static int step;

  static int run(int q, int r) {
    return (q * 3 + 1) * 10 + r;
  }

  static int tick() {
    return ++step;
  }

  int use() {
    return run(step, tick()) + run(2, 3);
  }
}
