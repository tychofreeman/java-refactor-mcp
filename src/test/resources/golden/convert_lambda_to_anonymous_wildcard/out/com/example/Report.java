package com.example;

import java.util.List;
import java.util.function.Consumer;

class Report {
  void all(List<String> skipped) {
    skipped.forEach(new Consumer<String>() {
      @Override
      public void accept(String s) {
        System.out.println("skipped " + s);
      }
    });
  }
}
