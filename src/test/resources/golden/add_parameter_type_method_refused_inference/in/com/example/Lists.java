package com.example;

import java.util.ArrayList;
import java.util.List;

class Lists {
  String first(List<String> xs) {
    return xs.get(0);
  }

  Runnable later() {
    return new Runnable() {
      @Override
      public void run() {
        Object o = first(new ArrayList<>());
        System.out.println(o);
      }
    };
  }
}
