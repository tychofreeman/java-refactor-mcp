package com.example;

import java.util.List;

class Renamer {
  static void log(String n) {
    System.out.println("renamed " + n);
  }

  static void carryOut(Main receiver, String n) {
    Renamer.log(n);
  }

  static void renameAll(Main receiver, List<String> names) {
    for (String n : names) {
      Renamer.carryOut(receiver, n);
    }
  }
}
