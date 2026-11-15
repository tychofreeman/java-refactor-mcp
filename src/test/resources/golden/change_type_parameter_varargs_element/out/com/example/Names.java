package com.example;

class Names {
  static String join(CharSequence... names) {
    return String.join(",", names);
  }

  static String ab() {
    return join("a", "b");
  }
}
