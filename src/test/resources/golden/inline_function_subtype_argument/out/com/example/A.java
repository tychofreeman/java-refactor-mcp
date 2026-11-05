package com.example;

public class A {
  int f(String t) {
    return Lengths.of(t);
  }
}

class Lengths {
  static int of(CharSequence s) {
    return s.length();
  }
}
