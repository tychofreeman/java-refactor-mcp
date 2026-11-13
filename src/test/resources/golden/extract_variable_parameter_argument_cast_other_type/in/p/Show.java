package p;

import java.io.Serializable;

class Show {
  static String show(Serializable x) {
    return String.valueOf(x);
  }

  String use(int n) {
    return show(n);
  }
}
