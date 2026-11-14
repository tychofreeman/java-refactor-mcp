package com.example;

public class Price {
  static class Tax {
    static double withTax(double amount) {
      return amount * 1.2;
    }
  }

  double total(double x) {
    return Tax.withTax(x) + 1;
  }
}

class Item {
  double cost;

  double gross() {
    return Price.Tax.withTax(cost);
  }
}
