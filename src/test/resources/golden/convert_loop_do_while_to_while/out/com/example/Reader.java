package com.example;

import java.util.Iterator;

public class Reader {
  String firstNonBlank(Iterator<String> lines) {
    String line;
    while (true) {
      line = lines.next();
      if (!(line.isBlank() && lines.hasNext())) break;
    }
    return line;
  }
}
