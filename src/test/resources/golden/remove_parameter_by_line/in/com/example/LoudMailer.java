package com.example;

class LoudMailer extends Mailer {
  @Override
  void send(String to, int retries) {
    System.out.println(to.toUpperCase());
  }
}
