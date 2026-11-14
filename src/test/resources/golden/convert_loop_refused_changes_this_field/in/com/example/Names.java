package com.example;

import java.util.ArrayList;
import java.util.List;

public class Names {
  private final List<String> names = new ArrayList<>();

  void prune() {
    for (String s : names) {
      if (s.isEmpty()) {
        this.names.remove(s);
      }
    }
  }
}
