package com.example;

class Commands {
  void dispatch(String command) {
    switch (command) {
      case "stop" -> System.out.println("stopping");
      default -> {
        System.out.println("unknown");
      }
    }
  }
}
