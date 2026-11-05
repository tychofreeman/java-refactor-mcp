package com.example;

import java.io.IOException;
import java.io.StringReader;

public class Price {
  int f(Item p) {
    try (StringReader r = new StringReader(p.name)) {
      return r.read();
    } catch (RuntimeException | IOException e) {
      return -1;
    }
  }
}

class Item {
  String name;
}
