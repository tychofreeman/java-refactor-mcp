package com.example;

class Cart {
  private final String owner;
  private final int size;

  Cart(String owner, int size) {
    this.owner = owner.strip();
    this.size = Math.max(0, size);
  }
}
