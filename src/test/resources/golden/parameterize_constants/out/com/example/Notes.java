package com.example;

class Notes {
  static String logEmail(String to) {
    return logTo(to, "email to %s");
  }

  static String logSms(String addr) {
    return logTo(addr, "sms to %s");
  }

  static void use() {
    logEmail("a@x");
    logSms("555");
  }

  private static String logTo(String to, String format) {
    return String.format(format, to);
  }
}
