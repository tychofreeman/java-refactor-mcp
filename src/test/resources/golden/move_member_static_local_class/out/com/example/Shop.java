package com.example;

public class Shop {
  static int rate() {
    return 2;
  }
}

class Tills {
  static int total(int a) {
    class Line {
      int price() {
        return a * Shop.rate();
      }
    }
    return new Line().price();
  }
}
