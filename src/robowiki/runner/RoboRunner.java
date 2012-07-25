package robowiki.runner;

import static robowiki.runner.RunnerUtil.getCombinedArgs;
import static robowiki.runner.RunnerUtil.parseArgument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import robowiki.runner.BattleRunner.BattleResultHandler;
import robowiki.runner.BattleRunner.BotSet;
import robowiki.runner.BattleRunner.RobotScore;
import robowiki.runner.ChallengeConfig.ScoringStyle;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class RoboRunner {
  private static final String PROPERTIES_FILENAME = "roborunner.properties";
  private static final String DATA_DIR = "data";
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private BattleRunner _battleRunner;
  private RunnerConfig _config;

  public static void main(String[] args) {
    args = getCombinedArgs(args);
    String challengerBot = parseArgument("bot", args,
        "ERROR: Pass a bot with -bot, eg: -bot voidious.Dookious 1.573c");
    String challengeFile = parseArgument("c", args,
        "ERROR: Pass a challenge file with -c, eg: -c challenges/testbed.rrc");
    int seasons = -1;
    try {
      seasons = Integer.parseInt(parseArgument("seasons", args,
          "ERROR: Pass number of seasons with -seasons, eg: -seasons 10"));
    } catch (NumberFormatException nfe) {
      // semi-expected
    }
    if (challengerBot == null || challengeFile == null || seasons == -1) {
      printHelp();
      return;
    }

    RoboRunner runner = new RoboRunner(challengerBot, challengeFile, seasons);
    runner.runBattles();
    runner.shutdown();
  }

  private static void printHelp() {
    PrintStream out = System.out;
    out.println();
    out.println("Usage: rr.sh -bot package.BotName 1.2.3 "
        + "-c challenge.rrc -seasons 25");
    out.println();
    out.println("Runs the challenger bot (-bot) against the challenge");
    out.println("specified in the .rrc file (-c), iterating over the");
    out.println("specified number of seasons (-seasons).");
    out.println();
    out.println("Robocode installs are specified in roborunner.properties.");
    out.println("One thread is used for each install. JARs missing from their");
    out.println("robots/ directories will be copied over from ./bots/, if");
    out.println("present.");
    out.println();
    out.println("Guava library should be placed in the lib dir, and rr.sh");
    out.println("must include it in the classpath. Available from:");
    out.println("  http://code.google.com/p/guava-libraries/");
    out.println();
    out.println("Format of the .rrc file is:");
    out.println("---");
    out.println("<Challenge name>");
    out.println("{PERCENT_SCORE|BULLET_DAMAGE|PERCENT_FIRSTS}");
    out.println("<Number of rounds>");
    out.println("<Battlefield width> (optional)");
    out.println("<Battlefield height> (optional)");
    out.println("package1.Bot1 1.0");
    out.println("package2.Bot2 1.0");
    out.println("package3.Bot3 1.0, package4.Bot4 1.0");
    out.println("---");
    out.println("Lines with opening or closing braces, as used in");
    out.println("RoboResearch to specify scoring groups, are ignored. All");
    out.println("scores are presently put into a single group. Lines with");
    out.println("multiple, comma delimited bots will be run as melee battles");
    out.println("with all the bots, with the score being the average");
    out.println("pair-wise score between the challenger and each of the bots.");
    out.println();
    out.println("Happy Robocoding!");
    out.println();
  }

  public RoboRunner(String challengerBot, String challengeFilePath,
      int seasons) {
    Preconditions.checkArgument(seasons > 0);
    _config = loadConfig(Preconditions.checkNotNull(challengerBot),
                         Preconditions.checkNotNull(challengeFilePath),
                         seasons);
    copyBots();

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

  private void copyBots() {
    System.out.println();
    System.out.print("Copying missing bots...");
    int jarsCopied = 0;
    List<BotSet> allBots =
        Lists.newArrayList(new BotSet(_config.challengerBot));
    allBots.addAll(_config.challenge.referenceBots);
    for (BotSet botSet : allBots) {
      for (String bot : botSet.getBotNames()) {
        String botJar = getBotJarName(bot);
        File sourceJar = new File("bots/" + botJar);
        for (String path : _config.robocodePaths) {
          File jarFile = new File(path + "/robots/" + botJar);
          if (!jarFile.exists()) {
            if (!sourceJar.exists()) {
              System.out.println(
                  "ERROR: Can't find " + sourceJar.getAbsolutePath());
            } else {
              try {
                System.out.print(".");
                jarsCopied++;
                Files.copy(sourceJar, jarFile);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }
    System.out.println(" " + jarsCopied + " JAR copies done!");
  }

  private String getBotJarName(String bot) {
    return bot.replaceAll(" ", "_") + ".jar";
  }

  public void runBattles() {
    System.out.println();
    System.out.println("Challenger: " + _config.challengerBot);
    System.out.println("Challenge:  " + _config.challenge.name);
    System.out.println("Seasons:    " + _config.seasons);
    System.out.println("Threads:    " + _config.robocodePaths.size());
    System.out.println();
    long startTime = System.nanoTime();

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
      public void processResults(
          Map<String, RobotScore> robotScoreMap, long nanoTime) {
        // TODO: handle other types of scoring
        double aps =
            getAveragePercentScore(robotScoreMap, _config.challengerBot);
        double firsts =
            getAverageSurvivalRounds(robotScoreMap, _config.challengerBot);
        double survival =
            getAverageSurvivalScore(robotScoreMap, _config.challengerBot);
        double bulletDamage =
            getAverageBulletDamage(robotScoreMap, _config.challengerBot);
        double energyConserved =
            getAverageEnergyConserved(robotScoreMap, _config.challengerBot);
        String botList = getSortedBotList(robotScoreMap, _config.challengerBot);
        addBattleScore(battleData, botList, aps, firsts, survival, bulletDamage,
            energyConserved);
        saveBattleData(battleData, _config.challengerBot);
        System.out.println("  " + _config.challengerBot + " vs " +
            botList.replace(",", ", ") + ": " + round(aps, 2) + ", took "
            + formatBattleTime(nanoTime));
        printOverallScore(battleData, _config.challenge.scoringStyle);
      }
    });

    System.out.println();
    System.out.println("Done! Took "
        + formatBattleTime(System.nanoTime() - startTime));
    System.out.println();
  }

  protected String formatBattleTime(long nanoTime) {
    return Double.toString(round((double) nanoTime / 1000000000, 1)) + "s";
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

  private void printOverallScore(
      Properties battleData, ScoringStyle scoringStyle) {
    // TODO: print score based on scoring style
    double totalScore = 0;
    int totalBattles = 0;
    int scoredBotLists = 0;
    for (String botList : battleData.stringPropertyNames()) {
      String[] scores = battleData.getProperty(botList).split(":");
      double score = Double.parseDouble(scores[0]);
      totalScore += score;
      scoredBotLists++;
      totalBattles += Integer.parseInt(scores[5]);
    }
    int challengeBotLists = _config.challenge.referenceBots.size();
    System.out.println("Overall score: " + round(totalScore / scoredBotLists, 2)
        + ", " + round(((double) totalBattles) / challengeBotLists, 2)
        + " seasons");
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
      double score, double firsts, double survival, double damage,
      double energy) {
    if (battleData.containsKey(botList)) {
      String[] scores = battleData.getProperty(botList).split(":");
      double oldScore = Double.parseDouble(scores[0]);
      double oldFirsts = Double.parseDouble(scores[1]);
      double oldSurvival = Double.parseDouble(scores[2]);
      double oldBulletDamage = Double.parseDouble(scores[3]);
      double oldEnergy = Double.parseDouble(scores[4]);
      int numBattles = Integer.parseInt(scores[5]);
      String updatedScore =
          ((oldScore * numBattles) + score) / (numBattles + 1) + ":"
          + ((oldFirsts * numBattles) + firsts) / (numBattles + 1) + ":"
          + ((oldSurvival * numBattles) + survival) / (numBattles + 1) + ":"
          + ((oldBulletDamage * numBattles) + damage) / (numBattles + 1) + ":"
          + ((oldEnergy * numBattles) + energy) / (numBattles + 1) + ":"
          + (numBattles + 1);
      battleData.setProperty(botList, updatedScore);
    } else {
      battleData.put(botList, score + ":" + firsts + ":" + survival + ":"
          + damage + ":" + energy + ":" + 1);
    }
  }

  private double getAveragePercentScore(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    return getAverageScore(scoreMap, challengerBot, new Scorer() {
      @Override
      public double getScore(RobotScore robotScore) {
        return robotScore.score;
      }
    });
  }

  private double getAverageSurvivalRounds(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    return getAverageScore(scoreMap, challengerBot, new Scorer() {
      @Override
      public double getScore(RobotScore robotScore) {
        return robotScore.survivalRounds;
      }
    });
  }

  private double getAverageSurvivalScore(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    return getAverageScore(scoreMap, challengerBot, new Scorer() {
      @Override
      public double getScore(RobotScore robotScore) {
        return robotScore.survivalScore;
      }
    });
  }

  private double getAverageBulletDamage(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    return getAverageScore(scoreMap, challengerBot, new Scorer() {
      @Override
      public double getScore(RobotScore robotScore) {
        return robotScore.bulletDamage;
      }
    });
  }

  private double getAverageEnergyConserved(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    if (scoreMap.size() == 2) {
      for (String bot : scoreMap.keySet()) {
        if (!bot.equals(challengerBot)) {
          return 100 - getAverageBulletDamage(scoreMap, bot);
        }
      }
    }
    return 0;
  }

  private double getAverageScore(
      Map<String, RobotScore> scoreMap, String challengerBot, Scorer scorer) {
    double totalScore = 0;
    double challengerScore = scorer.getScore(scoreMap.get(challengerBot));
    for (Map.Entry<String, RobotScore> scoreEntry : scoreMap.entrySet()) {
      if (!challengerBot.equals(scoreEntry.getKey())) {
        totalScore += 100 * (challengerScore
            / (challengerScore + scorer.getScore(scoreEntry.getValue())));
      }
    }
    return totalScore / (scoreMap.size() - 1);
  }

  private String getSortedBotList(
      Map<String, RobotScore> scoreMap, String challengerBot) {
    List<String> botList = Lists.newArrayList(scoreMap.keySet());
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

  private static interface Scorer {
    double getScore(RobotScore robotScore);
  }
}
