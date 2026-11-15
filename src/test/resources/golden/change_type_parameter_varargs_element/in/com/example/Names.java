package com.example;

class Names {
  static String join(String... names) {
    return String.join(",", names);
  }

  static String ab() {
    return join("a", "b");
  }
}
