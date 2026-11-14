package p;

class Use {
  String s(Box<String> box) {
    box.setValue("y");
    return box.getValue();
  }
}
