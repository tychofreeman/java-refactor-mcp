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
    return () -> service.put("b");
  }

  void twice() {
    int n = service.putAll("c", "d");
    System.out.println(n);
  }
}
