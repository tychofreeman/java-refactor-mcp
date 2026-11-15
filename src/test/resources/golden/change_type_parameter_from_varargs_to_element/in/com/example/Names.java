package com.example;

class Names {
  static boolean given(Object... parts) {
    return parts != null;
  }

  static boolean one() {
    return given("a");
  }
}
