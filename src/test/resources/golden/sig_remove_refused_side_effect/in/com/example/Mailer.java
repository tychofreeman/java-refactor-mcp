package com.example;

class Mailer {
  int sent;

  void send(String to, int retries) {
    System.out.println(to);
  }

  int next() {
    return ++sent;
  }

  void run() {
    send("a", next());
  }
}
