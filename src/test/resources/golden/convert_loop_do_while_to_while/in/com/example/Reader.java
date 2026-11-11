package com.example;

import java.util.Iterator;

public class Reader {
  String firstNonBlank(Iterator<String> lines) {
    String line;
    do {
      line = lines.next();
    } while (line.isBlank() && lines.hasNext());
    return line;
  }
}
