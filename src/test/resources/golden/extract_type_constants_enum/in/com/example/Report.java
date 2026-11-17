package com.example;

class Report {
  String label(Order o) {
    switch (o.state()) {
      case Order.OPEN:
        return "open";
      case Order.SHIPPED:
        return "on its way";
      default:
        return "done";
    }
  }
}
