package com.example;

import java.io.IOException;

class Fixed implements Source {
  @Override
  public CharSequence read() throws IOException, IllegalStateException {
    return "x";
  }
}
