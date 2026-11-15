package com.example;

class Price {
  int cents;

  int withTax() {
    return cents + cents / 5;
  }

  int total() {
    return withTax() * 2;
  }
}
