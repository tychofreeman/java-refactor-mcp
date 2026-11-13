package p;

class Doubler implements Op {
  public int apply(int q, int twice, String s) {
    return twice + s.length();
  }

  int use(int n) {
    Op op = (q, twice, s) -> q;
    return op.apply(n, n * 2, "a") + apply(3, 3 * 2, "b");
  }
}
