package com.example;

import java.io.FileReader;
import java.io.IOException;

class Reader {
  String log = "";

  void run() {
    try {
      log += "a";
    } catch (RuntimeException e) {
      log += "b";
    }
  }
}
