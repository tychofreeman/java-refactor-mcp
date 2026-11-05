package p;
public class Main {
  public static void main(String[] args) {
    System.out.println(new Mid().name() + " " + new Other().name());
  }
}
class Root {
  String name() { return "root"; }
}
class Base extends Root {
}
class Mid extends Base {
  String name() { return "mid"; }
}
class Other extends Base {
}
