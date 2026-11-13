package p;

class Main {
  static int run(int q, int r, int tenth) {
    return tenth + r;
  }

  int use() {
    return run(5, 3, 5 / 10);
  }
}
