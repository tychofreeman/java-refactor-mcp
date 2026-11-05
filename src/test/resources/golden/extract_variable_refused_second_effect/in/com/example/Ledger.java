package com.example;

import java.util.Iterator;

public class Ledger {
  void transfer(String from, String to, int qty) {}

  void apply(Picks p) {
    transfer(p.bin(), p.bin(), p.qty());
  }
}

class Picks {
  Iterator<String> words;

  String bin() {
    return words.next();
  }

  int qty() {
    return Integer.parseInt(words.next());
  }
}
