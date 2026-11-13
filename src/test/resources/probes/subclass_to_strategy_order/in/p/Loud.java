package p;

public class Loud extends Speaker {
  public Loud(String name) {
    super(name + "!");
  }

  public Loud() {
  }

  @Override
  String say(String word, int times) {
    return word.toUpperCase().repeat(times);
  }
}
