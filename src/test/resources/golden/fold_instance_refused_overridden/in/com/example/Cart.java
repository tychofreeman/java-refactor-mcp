package com.example;

public class Cart {
  int count;
  int price;

  int total() {
    return count * price;
  }

  int withShipping() {
    return count * price + 5;
  }
}

class BigCart extends Cart {
  @Override
  int total() {
    return 2 * count * price;
  }
}
