package com.example;

public class Price {
  int rate = 2;
}

class Shop {
  Price current = new Price();

  Price next() {
    return current;
  }

  void up() {
    next().rate += 1;
  }
}
