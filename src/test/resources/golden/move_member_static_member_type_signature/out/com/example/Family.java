package com.example;

import java.util.List;

class Family {
  static int count(List<Signatures.Call> calls, Signatures.Call.Kind kind) {
    int n = 0;
    for (Signatures.Call c : calls) {
      if (c.name().equals(kind.name())) {
        n++;
      }
    }
    return n;
  }
}
