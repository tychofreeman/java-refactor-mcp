package com.example;

class Jobs {
  Runnable job(StringBuilder log) {
    return
        new Runnable() {
          @Override
          public void run() {
            log.append("a");
            log.append("b");
          }
        };
  }
}
