package com.example;

import com.example.pile.Piles;

public class Shop {
  public static class Stack {}

  Object begin() {
    return Piles.fresh();
  }
}
