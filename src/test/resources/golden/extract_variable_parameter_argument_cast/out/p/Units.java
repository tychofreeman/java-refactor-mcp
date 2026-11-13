package p;

class Units {
  static long scale(long x, long scaled) {
    return scaled;
  }

  long use(int n, char c, int a, int b, long w) {
    return scale(n, (long) n * 1000000) + scale(c, (long) c * 1000000) + scale(a + b, (long) (a + b) * 1000000) + scale(w, w * 1000000) + scale(5L, 5L * 1000000);
  }
}
