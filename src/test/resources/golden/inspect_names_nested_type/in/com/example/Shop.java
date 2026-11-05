package com.example;

public class Shop {
  static class Cart {
    private int total;

    void add(int price) {
      total += price;
      total += 1;
    }
  }
}
