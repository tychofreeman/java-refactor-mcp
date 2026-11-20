package shop;

class Totals {
  static int sum(int[] xs, int bonus) {
    int total = 0;
    for (int x : xs) {
      total += x;
      total *= 2;
    }
    int other = 1;
    other += bonus;
    other *= 2;
    return total + other;
  }

  static int step(int acc, int x) {
    acc += x;
    acc *= 2;
    return acc;
  }
}
