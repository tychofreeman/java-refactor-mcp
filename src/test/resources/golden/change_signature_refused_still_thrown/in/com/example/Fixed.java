package com.example;

import java.io.IOException;

class Fixed implements Source {
  @Override
  public String read() throws IOException {
    if (this.hashCode() == 0) {
      throw new IOException("x");
    }
    return "x";
  }
}
