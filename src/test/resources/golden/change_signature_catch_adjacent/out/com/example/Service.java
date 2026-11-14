package com.example;

import java.io.IOException;
import java.io.UncheckedIOException;

class Service {
  private final Store store = s -> System.out.println(s);

  void putBoth(String a, String b) {
    try {
      store.save(a);
      store.save(b);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.out.println("done");
    try {
      store.save(a + b);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
