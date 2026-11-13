package com.example;

import java.util.ArrayList;
import java.util.List;

public class Use {
  List<Item> all(Cart cart) {
    List<Item> out = new ArrayList<>();
    cart.drainTo(out);
    return out;
  }
}
