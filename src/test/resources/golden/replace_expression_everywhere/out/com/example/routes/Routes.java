package com.example.routes;

import com.example.Shipment;

public final class Routes {
  private Routes() {}

  public static String of(Shipment s, String kind) {
    return kind + ":" + s.id();
  }
}
