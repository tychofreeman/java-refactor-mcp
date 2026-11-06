package lib.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AcceptTest {
  @Test
  void smsIsANotifier() {
    Notifier n = new SmsNotifier();
    assertEquals("sms", n.channel());
  }

  @Test
  void smsFormat() {
    assertEquals("SMS to 555: hi", new SmsNotifier().format("555", "hi"));
  }

  @Test
  void smsCutsTheTextAt140() {
    assertEquals("SMS to 5: " + "x".repeat(140), new SmsNotifier().format("5", "x".repeat(200)));
  }
}
