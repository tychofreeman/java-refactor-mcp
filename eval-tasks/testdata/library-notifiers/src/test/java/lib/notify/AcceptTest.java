package lib.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AcceptTest {
  @Test
  void sms() {
    Notifier n = new SmsNotifier();
    assertEquals("sms", n.channel());
    assertEquals("SMS to 555: hi", n.format("555", "hi"));
  }

  @Test
  void push() {
    Notifier n = new PushNotifier();
    assertEquals("push", n.channel());
    assertEquals("Push to ann: hi", n.format("ann", "hi"));
  }

  @Test
  void pager() {
    Notifier n = new PagerNotifier();
    assertEquals("pager", n.channel());
    assertEquals("PAGE 42: HI", n.format("42", "hi"));
  }
}
