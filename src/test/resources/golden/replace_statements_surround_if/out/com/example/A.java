package com.example;

import java.io.IOException;
import java.io.Reader;

class A {
  int read(Reader r, boolean ready) throws IOException {
    int n = 0;
    if (ready) {
      n = r.read();
      n++;
    }
    return n;
  }
}
