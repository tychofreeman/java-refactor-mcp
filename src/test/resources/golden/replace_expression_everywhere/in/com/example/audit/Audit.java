package com.example.audit;

import com.example.Shipment;

public class Audit {
  static boolean lacking(Shipment shipment) {
    return shipment.road() == null;
  }
}
