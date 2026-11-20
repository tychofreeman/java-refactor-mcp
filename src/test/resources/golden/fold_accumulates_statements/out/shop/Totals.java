package shop;

class Totals {
  static int sum(int[] xs, int bonus) {
    int total = 0;
    for (int x : xs) {
      total = step(total, x);
    }
    int other = 1;
    other = step(other, bonus);
    return total + other;
  }

  static int step(int acc, int x) {
    acc += x;
    acc *= 2;
    return acc;
  }
}
