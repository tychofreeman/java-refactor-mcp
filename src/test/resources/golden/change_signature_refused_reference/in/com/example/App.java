package com.example;

class App implements Runnable {
  private final Service service = new Service();

  @Override
  public void run() {
    String name = "a".trim();
    service.put(name);
    System.out.println(name);
  }

  Runnable later() {
    java.util.function.Consumer<String> c = service::put;
    return () -> c.accept("b");
  }

  void twice() {
    int n = service.putAll("c", "d");
    System.out.println(n);
  }
}
