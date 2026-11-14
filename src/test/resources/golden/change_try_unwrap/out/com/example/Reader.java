package com.example;

import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  void run(String path) throws IOException {
    new FileReader(path).close();
    log += "ok";
  }
}
