package com.example;

public final class Hold {
  private final String member;

  private final int position;

  public Hold(String member, int position) {
    this.member = member;
    this.position = position;
  }

  public int position() {
    return position;
  }
}
