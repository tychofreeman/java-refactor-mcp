package lib.notify;

/** Email: a To line, then the text. */
public class EmailNotifier implements Notifier {
  @Override
  public String channel() {
    return "email";
  }

  @Override
  public String format(String to, String text) {
    return "To: " + to + "\n" + text;
  }
}
