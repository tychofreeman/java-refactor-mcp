package com.shop;

import java.util.List;

/** Sums {@link Item#price} over its items. */
public class Cart {
  int total(List<Item> items) {
    int sum = 0;
    for (Item item : items) {
      sum += item.price;
    }
    return sum;
  }
}
