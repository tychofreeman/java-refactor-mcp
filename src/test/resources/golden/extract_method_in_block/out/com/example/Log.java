package com.example;

class Log {
  private final StringBuilder out = new StringBuilder();

  void write(String line, boolean header) {
    if (header) {
      banner();
    }
    out.append(line);
  }

  private void banner() {
    // the banner
    out.append("==");
    out.append('\n');
  }
}
