package p;

class Sub extends Base {
  @Override
  int run(int q, int twice, String s) {
    return q;
  }

  int use(Base b, int n) {
    return b.run(n, n * 2, label());
  }

  String label() {
    return "x";
  }
}
