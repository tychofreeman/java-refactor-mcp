package com.example;

class Price {
  int cents;

  int total() {
    return Tax.addTax(this) * 2;
  }
}
