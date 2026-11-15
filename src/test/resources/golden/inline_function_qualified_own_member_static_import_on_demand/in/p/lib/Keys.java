package p.lib;

public final class Keys {
  public static final String PREFIX = "k:";

  public static String declared(String name) {
    return PREFIX + name;
  }

  public static String erased(String name) {
    return Keys.declared(name) + Keys.PREFIX;
  }
}
