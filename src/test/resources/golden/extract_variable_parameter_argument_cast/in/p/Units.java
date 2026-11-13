package p;

class Units {
  static long scale(long x) {
    return x * 1000000;
  }

  long use(int n, char c, int a, int b, long w) {
    return scale(n) + scale(c) + scale(a + b) + scale(w) + scale(5L);
  }
}
