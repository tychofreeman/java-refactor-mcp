package com.example;

import java.util.List;

public class Names {
  void print(List<String> names) {
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      System.out.println(name);
    }
  }
}
