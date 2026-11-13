package shop;

class Ratio {
  int of(int a, int b) {
    if (b != 0) {
      return a / b;
    }
    return 0;
  }

  int use(int x) {
    return of(x, x - 1);
  }
}
