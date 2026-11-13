package p;

class Main {
  static int step;

  static int run(int q, int r, int z) {
    return r * 2 + q + z;
  }

  static int tick() {
    return ++step;
  }

  int use() {
    return run(tick(), step, tick());
  }
}
