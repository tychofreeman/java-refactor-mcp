package lib.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotifierTest {
  @Test
  void emailHasAToLine() {
    assertEquals("To: ann@x.org\nDue soon", new EmailNotifier().format("ann@x.org", "Due soon"));
  }

  @Test
  void sendJoinsEachChannel() {
    String sent =
        Notifications.send(List.of(new EmailNotifier(), new EmailNotifier()), "a", "b");
    assertEquals("To: a\nb\n---\nTo: a\nb", sent);
  }
}
