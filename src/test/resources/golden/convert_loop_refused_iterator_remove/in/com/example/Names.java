package com.example;

import java.util.Iterator;
import java.util.List;

public class Names {
  void prune(List<String> names) {
    for (Iterator<String> it = names.iterator(); it.hasNext(); ) {
      String name = it.next();
      if (name.isEmpty()) {
        it.remove();
      }
    }
  }
}
