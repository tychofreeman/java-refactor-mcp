package com.example;

import java.util.ArrayList;
import java.util.List;

class Use {
  int run() {
    Box b = new Box("a");
    String s = b.get();
    List<Box> all = new ArrayList<>();
    all.add(b.copy());
    return s.length() + new Box("abc").get().length() + all.size();
  }
}
