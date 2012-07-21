package robowiki.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import robocode.control.RobotResults;
import robowiki.runner.BattleRunner.BattleResultHandler;
import robowiki.runner.BattleRunner.BotSet;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class RoboRunner {
  private static final String PROPERTIES_FILENAME = "roborunner.properties";
  private static final String DATA_DIR = "data";
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private BattleRunner _battleRunner;
  private RunnerConfig _config;

  public static void main(String[] args) {
    args = getCombinedArgs(args);
    String challengerBot =
        Preconditions.checkNotNull(parseArgument("bot", args),
            "Pass a bot with -bot, eg: rr.sh -bot voidious.Dookious 1.573c");
    String challengeFile = Preconditions.checkNotNull(parseArgument("c", args),
        "Pass a challenge file with -c, eg: rr.sh -c challenges/testbed.rrc");
    int seasons = Integer.parseInt(
        Preconditions.checkNotNull(parseArgument("seasons", args),
            "Pass number of seasons with -seasons, eg: rr.sh -seasons 10"));

    RoboRunner runner = new RoboRunner(challengerBot, challengeFile, seasons);
    runner.runBattles();
    runner.shutdown();
  }

  private static String[] getCombinedArgs(String[] args) {
    List<String> argsList = Lists.newArrayList();
    String nextArg = "";
    for (String arg : args) {
      if (arg.startsWith("-")) {
        if (!nextArg.equals("")) {
          argsList.add(nextArg);
          nextArg = "";
        }
        argsList.add(arg);
      } else {
        nextArg = (nextArg + " " + arg).trim();
      }
    }
    if (!nextArg.equals("")) {
      argsList.add(nextArg);
    }
    return argsList.toArray(new String[0]);
  }

  private static String parseArgument(String flagName, String[] args) {
    for (int x = 0; x < args.length - 1; x++) {
      if (args[x].equals("-" + flagName)) {
        return args[x+1];
      }
    }
    return null;
  }

  public RoboRunner(String challengerBot, String challengeFilePath,
      int seasons) {
    Preconditions.checkArgument(seasons > 0);
    _config = loadConfig(Preconditions.checkNotNull(challengerBot),
                         Preconditions.checkNotNull(challengeFilePath),
                         seasons);
    _battleRunner = new BattleRunner(_config.robocodePaths,
        _config.challenge.rounds, _config.challenge.battleFieldWidth,
        _config.challenge.battleFieldHeight);
  }

  private RunnerConfig loadConfig(
      String challengerBot, String challengeFilePath, int seasons) {
    Properties runnerProperties = loadRoboRunnerProperties();
    Set<String> robocodePaths = Sets.newHashSet(
        runnerProperties.getProperty("robocodePaths").split(" *, *"));
    ChallengeConfig challenge = ChallengeConfig.load(challengeFilePath);
    return new RunnerConfig(robocodePaths, challenge, challengerBot, seasons);
  }

  private Properties loadRoboRunnerProperties() {
    Properties runnerProperties = new Properties();
    try {
      runnerProperties.load(new FileInputStream(PROPERTIES_FILENAME));
    } catch (FileNotFoundException e) {
      System.out.println("Couldn't find " + PROPERTIES_FILENAME + ". Run "
          + "setup.sh to configure your Robocode installs, or manually edit "
          + PROPERTIES_FILENAME + " to get started.");
      writeDefaultProperties();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return runnerProperties;
  }

  private void writeDefaultProperties() {
    Properties defaultProperties = new Properties();
    defaultProperties.setProperty("robocodePaths",
        "/home/pez/robocode_1740_1, /home/pez/robocode_1740_2");
    try {
      defaultProperties.store(new FileOutputStream(PROPERTIES_FILENAME), null);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void runBattles() {
    System.out.println();
    System.out.println("Challenger: " + _config.challengerBot);
    System.out.println("Challenge:  " + _config.challenge.name);
    System.out.println("Seasons:    " + _config.seasons);
    System.out.println("Threads:    " + _config.robocodePaths.size());
    System.out.println();

    final Properties battleData = loadBattleData(_config.challengerBot);
    Map<String, Integer> skipMap = getSkipMap(battleData);
    List<BotSet> battleSet = Lists.newArrayList();
    for (int x = 0; x < _config.seasons; x++) {
      for (BotSet botSet : _config.challenge.referenceBots) {
        List<String> battleBots = Lists.newArrayList(_config.challengerBot);
        List<String> botNames = Lists.newArrayList(botSet.getBotNames());
        if (!skip(skipMap, botNames)) {
          battleBots.addAll(botNames);
          battleSet.add(new BotSet(battleBots));
        }
      }
    }
    _battleRunner.runBattles(battleSet, new BattleResultHandler() {
      @Override
      public void processResults(Map<String, RobotResults> resultsMap) {
        // TODO: handle other types of scoring
        double aps = getAveragePercentScore(resultsMap, _config.challengerBot);
        String botList = getSortedBotList(resultsMap, _config.challengerBot);
        addBattleScore(battleData, botList, aps);
        saveBattleData(battleData, _config.challengerBot);
        System.out.println("    vs " + botList.replace(",", ", ") + ": "
            + round(aps, 2));
        printOverallScore(battleData);
      }
    });

    System.out.println();
    System.out.println("Done!");
    System.out.println();
  }

  private Properties loadBattleData(String challengerBot) {
    Properties properties = new Properties();
    File dataFile = new File(DATA_DIR + "/" + challengerBot + ".data");
    if (dataFile.exists()) {
      try {
        properties.load(new FileInputStream(dataFile));
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return properties;
  }

  private void saveBattleData(Properties battleData, String challengerBot) {
    try {
      battleData.store(
          new FileOutputStream(DATA_DIR + "/" + challengerBot + ".data"), null);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void printOverallScore(Properties battleData) {
    double totalScore = 0;
    int totalBattles = 0;
    for (String botList : battleData.stringPropertyNames()) {
      String[] scores = battleData.getProperty(botList).split(":");
      double score = Double.parseDouble(scores[0]);
      totalScore += score;
      totalBattles += Integer.parseInt(scores[1]);
    }
    int totalBotLists = _config.challenge.referenceBots.size();
    System.out.println("Overall score: " + round(totalScore / totalBotLists, 2)
        + ", " + round(((double) totalBattles) / totalBotLists, 2) + " seasons");
  }

  private Map<String, Integer> getSkipMap(Properties battleData) {
    Map<String, Integer> skipMap = Maps.newHashMap();
    for (String bot : battleData.stringPropertyNames()) {
      String propertyString = battleData.getProperty(bot);
      int numBattles = Integer.parseInt(
          propertyString.substring(propertyString.indexOf(":") + 1));
      skipMap.put(bot, numBattles);
    }
    return skipMap;
  }

  private boolean skip(Map<String, Integer> skipMap, List<String> botNames) {
    String sortedBotList = getSortedBotList(botNames);
    if (skipMap.containsKey(sortedBotList)) {
      int skipsLeft = skipMap.get(sortedBotList);
      if (skipsLeft > 0) {
        skipMap.put(sortedBotList, skipsLeft - 1);
        return true;
      }
    }
    return false;
  }

  private void addBattleScore(Properties battleData, String botList,
      double newScore) {
    if (battleData.containsKey(botList)) {
      String[] scores = battleData.getProperty(botList).split(":");
      double score = Double.parseDouble(scores[0]);
      int numBattles = Integer.parseInt(scores[1]);
      String updatedScore = ((score * numBattles) + newScore) / (numBattles + 1)
          + ":" + (numBattles + 1);
      battleData.setProperty(botList, updatedScore);
    } else {
      battleData.put(botList, newScore + ":" + 1);
    }
  }

  private double getAveragePercentScore(
      Map<String, RobotResults> resultsMap, String challengerBot) {
    double totalAveragePercentScore = 0;
    double challengerScore =
        resultsMap.get(_config.challengerBot).getScore();
    for (Map.Entry<String, RobotResults> results : resultsMap.entrySet()) {
      if (!_config.challengerBot.equals(results.getKey())) {
        totalAveragePercentScore += 100 * (challengerScore
            / (challengerScore + results.getValue().getScore()));
      }
    }
    return totalAveragePercentScore / (resultsMap.size() - 1);
  }

  private String getSortedBotList(Map<String, RobotResults> resultsMap,
      String challengerBot) {
    List<String> botList = Lists.newArrayList(resultsMap.keySet());
    botList.remove(challengerBot);
    return getSortedBotList(botList);
  }

  private String getSortedBotList(List<String> botList) {
    List<String> sortedBotList = Lists.newArrayList(botList);
    Collections.sort(sortedBotList);
    return COMMA_JOINER.join(sortedBotList);
  }

  public void shutdown() {
    _battleRunner.shutdown();
  }

  private double round(double d, int i) {
    long powerTen = 1;
    for (int x = 0; x < i; x++) {
      powerTen *= 10;
    }
    return ((double) Math.round(d * powerTen)) / powerTen;
  }

  private static class RunnerConfig {
    public final Set<String> robocodePaths;
    public final ChallengeConfig challenge;
    public final String challengerBot;
    public final int seasons;

    public RunnerConfig(Set<String> robocodePaths,
        ChallengeConfig challenge, String challengerBot, int seasons) {
      this.robocodePaths = Preconditions.checkNotNull(robocodePaths);
      this.challenge = challenge;
      this.challengerBot = Preconditions.checkNotNull(challengerBot);
      this.seasons = seasons;
    }
  }
}
