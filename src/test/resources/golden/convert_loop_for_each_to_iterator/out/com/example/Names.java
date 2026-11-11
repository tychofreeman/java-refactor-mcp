package com.example;

import java.util.Iterator;
import java.util.List;

public class Names {
  void print(List<String> names) {
    for (Iterator<String> it = names.iterator(); it.hasNext(); ) {
      final String name = it.next();
      System.out.println(name);
    }
  }
}
