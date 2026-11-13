package p;

class Sub extends Base {
  @Override
  int run(int q, String s) {
    return q;
  }

  int use(Base b, int n) {
    return b.run(n, label());
  }

  String label() {
    return "x";
  }
}
