package com.example;

public class Outer {
  static String a = "outer";

  static class Inner {
    static String b = "inner";

    static String run() {
      return a;
    }
  }
}
