package com.example;

import java.util.Iterator;

public class Lines {
  int skip(Iterator<String> lines) {
    String line = lines.next();
    while (line.isBlank()) {
      line = lines.next();
    }
    return line.length();
  }
}
