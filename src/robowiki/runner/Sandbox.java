package robowiki.runner;

import java.util.List;

import robowiki.runner.BattleRunner.BotSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Sandbox {
  public static void main(String[] args) {
    BattleRunner runner = new BattleRunner(
        Sets.newHashSet("/Users/pcupka/roborunner/r1741a",
                        "/Users/pcupka/roborunner/r1741b",
                        "/Users/pcupka/roborunner/r1741c"),
        35, 800, 600);
    List<BotSet> battleBots = Lists.newArrayList();
    for (int x = 0; x < 10; x++) {
      battleBots.add(
          new BotSet("voidious.Diamond 1.7.56", "jam.mini.Raiko 0.43"));
      battleBots.add(
          new BotSet("voidious.Diamond 1.7.56", "rz.HawkOnFire 0.1"));
      battleBots.add(
          new BotSet("voidious.Diamond 1.7.56", "sample.Crazy 1.0"));
      battleBots.add(
          new BotSet("voidious.Diamond 1.7.56", "kc.micro.MaxRisk 0.1"));
    }

    runner.runBattles(battleBots);
    runner.shutdownThreads();
  }
}
