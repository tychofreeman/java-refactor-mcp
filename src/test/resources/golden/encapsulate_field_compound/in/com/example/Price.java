package com.example;

public class Price {
  int rate = 2;

  void bump(int k) {
    rate += k + 1;
    rate *= 2;
  }
}

class Shop {
  void cut(Price p) {
    p.rate -= 3;
  }
}
