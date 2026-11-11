package com.example;

import java.util.List;

public class Main {
  private final String prefix = "p";

  void renameAll(List<String> names) {
    for (String n : names) {
      carryOut(n);
    }
  }

  private void carryOut(String n) {
    log(n);
  }

  private static void log(String n) {
    System.out.println("renamed " + n);
  }

  String other() {
    return prefix;
  }
}
