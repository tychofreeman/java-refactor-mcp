package p;

class Use {
  String s(Box<String> box) {
    box.value = "y";
    return box.value;
  }
}
