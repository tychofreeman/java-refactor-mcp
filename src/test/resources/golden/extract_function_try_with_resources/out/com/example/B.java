package com.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class B {
  int size(byte[] b, int n) throws IOException {
    copy(b, n);
    return n;
  }

  private void copy(byte[] b, int n) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      out.write(b, 0, n);
    }
  }
}
