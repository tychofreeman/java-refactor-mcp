package com.example;

public class Shipping {
  int cost(int weight, Size size) {
    return weight * size.width() * size.height();
  }

  int total() {
    return cost(2, new Size(3, 4));
  }
}
