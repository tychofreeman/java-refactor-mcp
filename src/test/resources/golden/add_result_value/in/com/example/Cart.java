package com.example;

class Cart {
  int total(int a) {
    if (a < 0) {
      return 0;
    }
    return a * 2;
  }

  void use() {
    int t = total(3);
    total(4);
  }
}
