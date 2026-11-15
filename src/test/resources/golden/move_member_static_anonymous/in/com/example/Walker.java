package com.example;

import java.util.List;

class Walker {
  private final List<String> names;

  Walker(List<String> names) {
    this.names = names;
  }

  void walk(Visitor v) {
    for (String n : names) {
      if (v.visit(n)) {
        v.leave(n);
      }
    }
  }
}
