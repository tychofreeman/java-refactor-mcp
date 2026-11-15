package com.example;

import java.util.List;
import java.util.function.Function;

class Use {
  String first(Item item) {
    String subKey = item.subKey();
    return subKey;
  }

  List<String> keys(List<Item> items) {
    Function<Item, String> f = Item::subKey;
    return items.stream().map(f).toList();
  }
}
