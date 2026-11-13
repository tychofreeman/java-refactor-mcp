package com.example;

class Log implements Sink {
  @Override
  public <T extends CharSequence> void put(T t) {
    System.out.println(t);
  }

  void run() {
    put("a");
  }
}
