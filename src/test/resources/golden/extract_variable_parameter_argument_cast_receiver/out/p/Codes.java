package p;

class Codes {
  static int code(Long x, int h) {
    return h + 1;
  }

  int use(long v) {
    return code(v, ((Long) v).hashCode());
  }
}
