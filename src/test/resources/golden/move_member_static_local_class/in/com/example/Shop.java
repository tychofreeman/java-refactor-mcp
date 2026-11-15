package com.example;

public class Shop {
  static int rate() {
    return 2;
  }

  static int total(int a) {
    class Line {
      int price() {
        return a * rate();
      }
    }
    return new Line().price();
  }
}

class Tills {}
