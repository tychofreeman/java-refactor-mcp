package com.example;

class Base {
  int rate() {
    return 2;
  }
}

public class Price {
  public int total(int a) {
    return a * rate();
  }
}
