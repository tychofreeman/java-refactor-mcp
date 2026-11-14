package com.example;

import java.util.ArrayList;
import java.util.List;

public class Main {
  private final List<String> names = new ArrayList<>();

  void grow() {
    for (String name : names) {
      this.names.add(name + "!");
    }
  }
}
