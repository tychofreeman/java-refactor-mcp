package p;

class Codes {
  static int code(Long x) {
    return x.hashCode() + 1;
  }

  int use(long v) {
    return code(v);
  }
}
