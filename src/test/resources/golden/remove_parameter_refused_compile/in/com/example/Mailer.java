package com.example;

public class Mailer {
  void send(String to, boolean copy) {
    System.out.println(to);
  }

  void send(String to) {
    send(to, false);
  }
}
