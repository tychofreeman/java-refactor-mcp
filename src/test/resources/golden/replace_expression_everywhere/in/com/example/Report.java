package com.example;

public class Report {
  static String line(Shipment s) {
    return s.id() + " by " + s.road();
  }

  static int length(Shipment s, Route r) {
    return s.road().length() + r.road().length();
  }
}
