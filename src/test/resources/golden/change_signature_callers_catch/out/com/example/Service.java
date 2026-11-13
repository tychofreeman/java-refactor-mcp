package com.example;

import java.io.IOException;
import java.io.UncheckedIOException;

class Service {
  private final Store store = new FileStore();

  void put(String s) {
    try {
      store.save(s);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int putAll(String a, String b) {
    try {
      store.save(a);
      store.save(b);
    } catch (IllegalArgumentException e) {
      return 0;
    } catch (IOException e2) {
      throw new UncheckedIOException(e2);
    }
    return 2;
  }
}
