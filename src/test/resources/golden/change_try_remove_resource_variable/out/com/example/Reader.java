package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  FileReader open(String path) throws IOException {
    return new FileReader(path);
  }

  void run(FileReader r) throws IOException {
    try {
      log += (char) r.read();
    } finally {
      if (r != null) {
        r.close();
      }
    }
  }
}
