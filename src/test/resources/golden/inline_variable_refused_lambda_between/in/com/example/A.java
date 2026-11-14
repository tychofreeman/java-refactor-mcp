package com.example;

import java.util.List;

public class A {
  int count;

  int f(List<String> names) {
    int v = count;
    names.forEach(
        n -> {
          count++;
        });
    return v;
  }
}
