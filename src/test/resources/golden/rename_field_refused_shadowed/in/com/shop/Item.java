package com.shop;

public class Item {
  int price;

  int withTax() {
    int cost = 2;
    return price + cost;
  }
}
