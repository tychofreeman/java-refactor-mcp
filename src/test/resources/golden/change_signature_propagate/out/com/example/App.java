package com.example;

import java.io.IOException;
import java.io.UncheckedIOException;

class App implements Runnable {
  private final Service service = new Service();

  @Override
  public void run() {
    String name = "a".trim();
    try {
      service.put(name);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.out.println(name);
  }

  Runnable later() {
    return () -> {
      try {
        service.put("b");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  void twice() throws IOException {
    int n = service.putAll("c", "d");
    System.out.println(n);
  }
}
