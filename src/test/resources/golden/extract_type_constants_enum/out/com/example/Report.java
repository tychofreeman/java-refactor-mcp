package com.example;

class Report {
  String label(Order o) {
    switch (o.state()) {
      case OPEN:
        return "open";
      case SHIPPED:
        return "on its way";
      default:
        return "done";
    }
  }
}
