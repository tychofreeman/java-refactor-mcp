package com.example;

class Use {
  int run() {
    Holder h = new Holder("a", 1);
    String s = h.value();
    return s.length() + h.twice().count();
  }
}
