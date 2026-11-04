package com.example;

class Lib {
  static final int FACTOR = 10;

  static int scale(int x) {
    return x * FACTOR;
  }
}

public class Price {
  static final int FACTOR = 2;

  public int total() {
    return Lib.scale(1);
  }
}
