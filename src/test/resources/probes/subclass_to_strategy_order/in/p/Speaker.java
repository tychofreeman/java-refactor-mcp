package p;

public class Speaker {
  static String log = "";
  final String name;

  public Speaker(String name) {
    log += "base(" + name + ")";
    this.name = name;
  }

  public Speaker() {
    this("anon");
  }

  String say(String word, int times) {
    return word;
  }

  String twice(String word) {
    return name + ":" + say(word, 1) + say(word, 2);
  }
}
