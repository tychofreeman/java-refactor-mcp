package com.example;

import java.util.List;

public class Main {
  void first(List<String> names) {
    for (String name : names) {
      if (name.isEmpty()) {
        break;
      }
      System.out.println(name);
    }
  }
}
