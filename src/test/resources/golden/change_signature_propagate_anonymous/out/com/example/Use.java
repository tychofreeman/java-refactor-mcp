package com.example;

import java.io.IOException;

class Use {
  Base make() throws IOException {
    return new Base(1) {
      @Override
      void run() {}
    };
  }
}
