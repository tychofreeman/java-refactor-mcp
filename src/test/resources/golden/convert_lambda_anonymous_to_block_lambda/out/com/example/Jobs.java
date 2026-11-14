package com.example;

class Jobs {
  Runnable job(StringBuilder log) {
    return
        () -> {
          log.append("a");
          log.append("b");
        };
  }
}
