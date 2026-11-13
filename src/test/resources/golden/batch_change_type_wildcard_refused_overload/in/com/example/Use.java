package com.example;

import java.util.List;

public class Use {
  void fill(Cart cart, List<Item> items, List<Book> books) {
    cart.addAll(items);
    // A List<Book> can only mean the Collection overload today.
    cart.addAll(books);
  }
}
