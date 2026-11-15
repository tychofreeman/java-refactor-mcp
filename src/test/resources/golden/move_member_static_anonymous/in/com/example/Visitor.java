package com.example;

abstract class Visitor {
  abstract boolean visit(String name);

  void leave(String name) {}
}
