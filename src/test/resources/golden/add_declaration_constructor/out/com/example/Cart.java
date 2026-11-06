package com.example;

import java.util.List;

public class Cart {
  private final String owner;
  private final List<String> items;
  private int count;

  public Cart(String owner, List<String> items) {
    this.owner = owner;
    this.items = items;
  }
}
