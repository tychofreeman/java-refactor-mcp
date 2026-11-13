package p;

class Main {
  static int step;

  static int run(int q, int r, int twice, int z) {
    return twice + q + z;
  }

  static int tick() {
    return ++step;
  }

  int use() {
    return run(tick(), step, step * 2, tick());
  }
}
