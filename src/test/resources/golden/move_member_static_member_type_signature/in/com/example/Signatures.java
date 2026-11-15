package com.example;

import java.util.List;

public class Signatures {
  record Call(String name) {
    enum Kind {
      A
    }
  }

  static int count(List<Call> calls, Call.Kind kind) {
    int n = 0;
    for (Call c : calls) {
      if (c.name().equals(kind.name())) {
        n++;
      }
    }
    return n;
  }
}
