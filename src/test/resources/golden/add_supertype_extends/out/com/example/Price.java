package com.example;

class Base {
  int rate() {
    return 2;
  }
}

public class Price extends Base {
  public int total(int a) {
    return a * rate();
  }
}
