package com.example.audit;

import com.example.Shipment;
import com.example.routes.Routes;

public class Audit {
  static boolean lacking(Shipment shipment) {
    return Routes.of(shipment, "road") == null;
  }
}
