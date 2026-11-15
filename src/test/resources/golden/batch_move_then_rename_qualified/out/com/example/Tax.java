package com.example;

class Tax {
  static int addTax(Price receiver) {
    return receiver.cents + receiver.cents / 5;
  }
}
