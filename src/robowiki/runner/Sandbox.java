package robowiki.runner;

public class Sandbox {
  public static void main(String[] args) {
    RoboRunner runner = new RoboRunner("voidious.Diamond 1.7.56",
        "/Users/pcupka/roborunner/simplebed.rrc", 10);
    runner.runBattles();
    runner.shutdown();
  }
}
