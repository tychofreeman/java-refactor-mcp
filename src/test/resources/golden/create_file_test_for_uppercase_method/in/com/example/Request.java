package com.example;

final class Request {
  private final String url;

  private Request(String url) {
    this.url = url;
  }

  static Request GET(String url) {
    return new Request(url);
  }
}
