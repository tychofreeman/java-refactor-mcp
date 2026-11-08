package com.example;

public class Price {
  int rate = 2;

  void tick() {
    rate++;
    --this.rate;
  }
}

class Shop {
  void up(Price p) {
    ++p.rate;
  }
}
