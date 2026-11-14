package com.example;

class Service {
  private final Store store = s -> System.out.println(s);

  void putBoth(String a, String b) {
    store.save(a);
    store.save(b);
    System.out.println("done");
    store.save(a + b);
  }
}
