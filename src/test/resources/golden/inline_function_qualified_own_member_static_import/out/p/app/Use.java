package p.app;

import p.lib.Keys;

public class Use {
  String key(String n) {
    return Keys.declared(n) + Keys.PREFIX;
  }
}
