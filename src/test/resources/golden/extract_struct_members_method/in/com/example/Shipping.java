package com.example;

public class Shipping {
  int cost(int weight, int width, int height) {
    return weight * width * height;
  }

  int total() {
    return cost(2, 3, 4);
  }
}
