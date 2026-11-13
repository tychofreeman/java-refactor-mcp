package com.example;

import java.io.IOException;

class Service {
  private final Store store = new FileStore();

  void put(String s) throws IOException {
    store.save(s);
  }

  int putAll(String a, String b) throws IOException {
    try {
      store.save(a);
      store.save(b);
    } catch (IllegalArgumentException e) {
      return 0;
    }
    return 2;
  }
}
