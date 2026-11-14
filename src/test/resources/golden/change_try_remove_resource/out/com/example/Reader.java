package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  FileReader open(String path) throws IOException {
    return new FileReader(path);
  }

  void run(String path) throws IOException {
    FileReader r = open(path);
    try {
      log += (char) r.read();
    } finally {
      if (r != null) {
        r.close();
      }
    }
    log += "!";
  }
}
