package com.example;

class Notes {
  static String logEmail(String to) {
    return String.format("email to %s", to);
  }

  static String logSms(String addr) {
    return String.format("sms to %s", addr);
  }

  static void use() {
    logEmail("a@x");
    logSms("555");
  }
}
