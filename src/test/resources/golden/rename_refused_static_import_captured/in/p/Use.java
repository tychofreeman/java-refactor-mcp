package p;

import static p.Util.twice;

class Use {
  static int run() {
    return twice(3);
  }

  static int other(int x) {
    return x;
  }
}
