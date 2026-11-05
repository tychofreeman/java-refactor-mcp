package com.example;

public class A {
  static int size(CharSequence s) {
    return Lengths.of(s);
  }

  int f(String t) {
    return size(t);
  }
}

class Lengths {
  static int of(CharSequence s) {
    return s.length();
  }

  static int of(String s) {
    return -1;
  }
}
