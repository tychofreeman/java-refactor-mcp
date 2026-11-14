package com.example;

public class Outer {
  static class Base {}

  static class Sub extends Base {
    int k() {
      return 1;
    }
  }
}
