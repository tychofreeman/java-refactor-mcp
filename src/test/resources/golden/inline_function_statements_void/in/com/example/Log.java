package com.example;

public class Log {
  static StringBuilder out = new StringBuilder();

  static void both(String a, String b) {
    out.append(a);
    out.append(b);
  }

  static void run(boolean go) {
    if (go) {
      both("x", "y");
    }
  }
}
