package com.example;

class Tax {
  static int withTax(Price receiver) {
    return receiver.cents + receiver.cents / 5;
  }
}
