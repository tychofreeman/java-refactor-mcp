package com.example;

class LoudMailer extends Mailer {
  @Override
  void send(String to) {
    System.out.println(to.toUpperCase());
  }
}
