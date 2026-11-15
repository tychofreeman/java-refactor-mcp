package com.example;

import java.io.IOException;
import java.io.Reader;

class A {
  int read(Reader r) throws IOException {
    int n = r.read();
    n++;
    return n;
  }
}
