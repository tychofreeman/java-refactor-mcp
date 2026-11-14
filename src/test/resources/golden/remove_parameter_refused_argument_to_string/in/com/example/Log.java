package com.example;

class Log {
  static String line(String text, String note) {
    return text;
  }

  static String of(Object item) {
    return line("item", "seen " + item);
  }
}
