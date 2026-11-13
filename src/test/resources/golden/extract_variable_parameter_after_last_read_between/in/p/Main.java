package p;

class Main {
  static int step;

  static int run(int q, int r, int z) {
    return (q + r) * z;
  }

  static int tick() {
    return ++step;
  }

  int use() {
    return run(step, tick(), 5);
  }
}
