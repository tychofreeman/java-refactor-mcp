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
    try (FileReader r = open(path)) {
      log += (char) r.read();
    }
    log += "!";
  }
}
