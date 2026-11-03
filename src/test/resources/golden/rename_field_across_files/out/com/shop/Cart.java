package com.shop;

import java.util.List;

/** Sums {@link Item#cost} over its items. */
public class Cart {
  int total(List<Item> items) {
    int sum = 0;
    for (Item item : items) {
      sum += item.cost;
    }
    return sum;
  }
}
