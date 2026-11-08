package com.example;

class Cart {
  final String owner;

  Cart(String owner) {
    this.owner = owner;
  }

  Cart() {
    this("nobody");
  }
}
