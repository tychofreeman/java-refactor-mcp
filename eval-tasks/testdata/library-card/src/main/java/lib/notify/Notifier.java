package lib.notify;

/** A way to tell a member something. */
public interface Notifier {
  /** The channel's name, such as email. */
  String channel();

  /** The message as this channel sends it to {@code to}. */
  String format(String to, String text);
}
