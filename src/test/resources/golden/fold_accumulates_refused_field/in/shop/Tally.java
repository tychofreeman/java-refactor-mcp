package shop;

class Tally {
  static int total;

  static void add(int x) {
    total += x;
  }

  static int step(int acc, int x) {
    acc += x;
    return acc;
  }
}
