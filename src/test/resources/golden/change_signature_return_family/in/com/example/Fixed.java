package com.example;

import java.io.IOException;

class Fixed implements Source {
  @Override
  public String read() throws IOException {
    return "x";
  }
}
