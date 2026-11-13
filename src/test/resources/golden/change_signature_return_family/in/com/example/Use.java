package com.example;

import java.io.IOException;

class Use {
  String all(Source s) throws IOException {
    return s.read().toString() + s.read();
  }
}
