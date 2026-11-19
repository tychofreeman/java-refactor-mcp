package com.example;

import com.example.routes.Routes;

public class Report {
  static String line(Shipment s) {
    return s.id() + " by " + Routes.of(s, "road");
  }

  static int length(Shipment s, Route r) {
    return Routes.of(s, "road").length() + r.road().length();
  }
}
