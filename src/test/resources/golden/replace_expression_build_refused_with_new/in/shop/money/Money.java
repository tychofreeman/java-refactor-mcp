package shop.money;

public class Money {
  public static Money zero() {
    return new Money();
  }

  public Money plus(Money other) {
    return this;
  }
}
