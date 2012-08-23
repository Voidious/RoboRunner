package robowiki.runner;

import static robowiki.runner.RunnerUtil.getCombinedArgs;
import static robowiki.runner.RunnerUtil.parseStringArgument;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotResults;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class BattleProcess {
  public static final String READY_SIGNAL = "BattleProcess ready";
  public static final String RESULT_SIGNAL = "BATTLE RESULT: ";
  public static final String BOT_DELIMITER = ":::";
  public static final String SCORE_DELIMITER = "::";

  private static final Joiner COMMA_JOINER = Joiner.on(",");
  private static final Joiner COLON_JOINER = Joiner.on(BOT_DELIMITER);

  private BattlefieldSpecification _battlefield;
  private int _numRounds;
  private RobocodeEngine _engine;
  private BattleListener _listener;

  public static void main(String[] args) {
    args = getCombinedArgs(args);
    String robocodePath = parseStringArgument("path", args,
        "Pass a path to Robocode with -path");
    int numRounds = Integer.parseInt(parseStringArgument("rounds", args,
        "Pass number of rounds width with -rounds"));
    int width = Integer.parseInt(parseStringArgument("width", args,
        "Pass battlefield width with -width"));
    int height = Integer.parseInt(parseStringArgument("height", args,
        "Pass battlefield height with -height"));

    BattleProcess process =
        new BattleProcess(robocodePath, numRounds, width, height);
    System.out.println(READY_SIGNAL);
    BufferedReader stdin =
        new BufferedReader(new java.io.InputStreamReader(System.in));
    while (true) {
      try {
        String line = stdin.readLine();
        System.out.println("Processing " + line);
        String result = process.runBattle(getBotList(line));
        System.out.println(RESULT_SIGNAL + result);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static BotList getBotList(String line) {
    return new BotList(Lists.newArrayList(line.split(",")));
  }

  public BattleProcess(String robocodePath, int numRounds,
      int battleFieldWidth, int battleFieldHeight) {
    _numRounds = numRounds;
    _battlefield =
        new BattlefieldSpecification(battleFieldWidth, battleFieldHeight);
    _engine = new RobocodeEngine(new File(robocodePath));
    _listener = new BattleListener();
    _engine.addBattleListener(_listener);
    _engine.setVisible(false);
  }

  public String runBattle(BotList botList) {
    BattleSpecification battleSpec = new BattleSpecification(
        _numRounds, _battlefield, 
    _engine.getLocalRepository(COMMA_JOINER.join(botList.getBotNames())));
    _engine.runBattle(battleSpec, true);
    Multimap<String, RobotResults> resultsMap = _listener.getRobotResultsMap();
    _listener.clear();
    return battleResultString(resultsMap);
  }

  private String battleResultString(Multimap<String, RobotResults> resultsMap) {
    Set<String> resultStrings = Sets.newHashSet();
    for (Map.Entry<String, RobotResults> resultsEntry : resultsMap.entries()) {
      RobotResults results = resultsEntry.getValue();
      resultStrings.add(resultsEntry.getKey() + SCORE_DELIMITER
          + results.getScore() + SCORE_DELIMITER
          + results.getFirsts() + SCORE_DELIMITER
          + results.getSurvival() + SCORE_DELIMITER
          + results.getBulletDamage());
    }
    return COLON_JOINER.join(resultStrings);
  }
}
