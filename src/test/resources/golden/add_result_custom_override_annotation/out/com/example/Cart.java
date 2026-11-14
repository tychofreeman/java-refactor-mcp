package com.example;

class Cart {
  @MayOverride
  TotalResult total(int a) {
    if (a < 0) {
      return new TotalResult(0, 0);
    }
    return new TotalResult(a * 2, 0);
  }

  record TotalResult(int value, int count) {}

  void use() {
    int t = total(3).value();
    total(4);
  }
}
