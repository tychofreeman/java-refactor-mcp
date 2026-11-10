package com.example;

import java.util.List;

public class Main {
  void grow(List<String> names) {
    for (String name : names) {
      names.add(name + "!");
    }
  }
}
