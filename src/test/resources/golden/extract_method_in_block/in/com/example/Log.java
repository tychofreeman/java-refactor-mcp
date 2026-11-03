package com.example;

class Log {
  private final StringBuilder out = new StringBuilder();

  void write(String line, boolean header) {
    if (header) {
      // the banner
      out.append("==");
      out.append('\n');
    }
    out.append(line);
  }
}
