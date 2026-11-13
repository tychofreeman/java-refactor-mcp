package com.example;

public class Use {
  private Box<String> field = new Box<>("f");
  private Object held = new Box<String>("o");

  Box<String> declared() {
    Box<String> b = new Box<>("a");
    b = new Box<>("b");
    Box<String> c = (new Box<>("c"));
    return new Box<>("r");
  }

  Object elsewhere() {
    var v = new Box<String>("v");
    Object o = new Box<String>("o");
    take(new Box<String>("t"));
    String s = new Box<String>("s").get();
    Runnable r = () -> take(new Box<String>("l"));
    return new Box<String>("x");
  }

  void take(Box<String> box) {}
}
