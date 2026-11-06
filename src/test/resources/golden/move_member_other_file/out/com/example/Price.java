package com.example;

class Price {
  int cents;

  int total() {
    return Tax.withTax(this) * 2;
  }
}
