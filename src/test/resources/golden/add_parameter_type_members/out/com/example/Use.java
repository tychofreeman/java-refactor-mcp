package com.example;

import java.util.ArrayList;
import java.util.List;

class Use {
  int run() {
    Box<String> b = new Box<>("a");
    String s = b.get();
    List<Box<String>> all = new ArrayList<>();
    all.add(b.copy());
    return s.length() + new Box<String>("abc").get().length() + all.size();
  }
}
