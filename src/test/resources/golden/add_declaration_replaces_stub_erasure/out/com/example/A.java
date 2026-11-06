package com.example;

import java.util.List;

class A {
  boolean check(List<String> items) {
    return allPure(items);
  }

  boolean allPure(List<?> items) {
    return items.isEmpty();
  }
}
