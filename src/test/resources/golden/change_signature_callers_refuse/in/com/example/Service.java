package com.example;

class Service {
  private final Store store = new FileStore();

  void put(String s) {
    store.save(s);
  }

  int putAll(String a, String b) {
    try {
      store.save(a);
      store.save(b);
    } catch (IllegalArgumentException e) {
      return 0;
    }
    return 2;
  }
}
