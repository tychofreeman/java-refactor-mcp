package com.example;

import java.util.List;

class Report {
  void all(List<String> skipped) {
    skipped.forEach(s -> System.out.println("skipped " + s));
  }
}
