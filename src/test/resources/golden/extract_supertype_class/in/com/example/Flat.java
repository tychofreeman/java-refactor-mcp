package com.example;

public class Flat {
  int base = 1;

  int price(int qty) {
    return qty + base;
  }

  String label() {
    return "flat";
  }
}
