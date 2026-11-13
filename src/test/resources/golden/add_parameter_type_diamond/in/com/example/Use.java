package com.example;

public class Use {
  private Box field = new Box("f");
  private Object held = new Box("o");

  Box declared() {
    Box b = new Box("a");
    b = new Box("b");
    Box c = (new Box("c"));
    return new Box("r");
  }

  Object elsewhere() {
    var v = new Box("v");
    Object o = new Box("o");
    take(new Box("t"));
    String s = new Box("s").get();
    Runnable r = () -> take(new Box("l"));
    return new Box("x");
  }

  void take(Box box) {}
}
