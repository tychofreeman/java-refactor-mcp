package com.example;

class App {
  static final int RETRIES = 2;

  void run(Mailer m, String addr) {
    m.send(addr, 3);
    new LoudMailer().send("x", RETRIES);
  }
}
