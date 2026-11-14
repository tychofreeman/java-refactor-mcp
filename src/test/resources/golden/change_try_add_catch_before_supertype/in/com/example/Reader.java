package com.example;

import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  void run(String path) throws IOException {
    try {
      new FileReader(path).close();
    } catch (Exception e) {
      log += "any";
    }
  }
}
