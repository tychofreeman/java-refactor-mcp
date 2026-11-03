package com.example;

class Mailer {
  static class Outbox {
    int retries = 3;

    int send(String to) {
      return retries;
    }
  }
}
