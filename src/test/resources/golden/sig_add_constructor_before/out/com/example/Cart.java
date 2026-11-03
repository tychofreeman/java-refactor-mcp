package com.example;

class Cart {
  final String owner;

  Cart(int capacity, String owner) {
    this.owner = owner;
  }

  Cart() {
    this(10, "nobody");
  }
}
