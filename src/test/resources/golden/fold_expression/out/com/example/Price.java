package com.example;

public class Price {
  static double withTax(double amount) {
    return amount * 1.2;
  }

  double total(double x) {
    return withTax(x) + 1;
  }
}

class Item {
  double cost;

  double gross() {
    return Price.withTax(cost);
  }
}
