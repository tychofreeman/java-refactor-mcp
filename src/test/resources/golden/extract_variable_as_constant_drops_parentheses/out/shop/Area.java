package shop;

class Area {
  static final int BASE = 1;

  private static final int TOP = BASE + 3;

  int scaled(int a) {
    return TOP * a;
  }
}
