package com.example;

import java.util.List;

class Tasks {
  static void run(List<String> names) {
    names.forEach(
        n -> {
          shout(n);
        });
  }

  private static void shout(String n) {
    String up = n.toUpperCase();
    System.out.println(up);
  }
}
