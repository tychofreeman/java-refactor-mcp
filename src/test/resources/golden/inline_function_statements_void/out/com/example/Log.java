package com.example;

public class Log {
  static StringBuilder out = new StringBuilder();

  static void run(boolean go) {
    if (go) {
      out.append("x");
      out.append("y");
    }
  }
}
