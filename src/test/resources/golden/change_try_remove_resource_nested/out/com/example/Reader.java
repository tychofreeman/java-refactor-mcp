package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  FileReader open(String path) throws IOException {
    return new FileReader(path);
  }

  void run(String path) {
    try {
      FileReader f = new FileReader(path);
      try (BufferedReader b = new BufferedReader(f)) {
        log += b.readLine();
      } finally {
        f.close();
      }
    } catch (IOException e) {
      log += "io";
    }
  }
}
