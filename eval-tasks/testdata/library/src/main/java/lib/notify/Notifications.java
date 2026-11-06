package lib.notify;

import java.util.ArrayList;
import java.util.List;

/** Sends one message through several channels. */
public final class Notifications {
  private Notifications() {}

  /** Each notifier's form of the message, in order, joined by a line of three dashes. */
  public static String send(List<Notifier> notifiers, String to, String text) {
    List<String> out = new ArrayList<>();
    for (Notifier n : notifiers) {
      out.add(n.format(to, text));
    }
    return String.join("\n---\n", out);
  }
}
