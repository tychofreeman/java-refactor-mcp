package p;

class Doubler implements Op {
  public int apply(int q, String s) {
    return q * 2 + s.length();
  }

  int use(int n) {
    Op op = (q, s) -> q;
    return op.apply(n, "a") + apply(3, "b");
  }
}
