package robowiki.runner;

import static robowiki.runner.RunnerUtil.getCombinedArgs;
import static robowiki.runner.RunnerUtil.parseStringArgument;
import static robowiki.runner.RunnerUtil.parseBooleanArgument;
import static robowiki.runner.RunnerUtil.round;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import robowiki.runner.BattleRunner.BattleResultHandler;
import robowiki.runner.ChallengeConfig.BotListGroup;
import robowiki.runner.RobotScore.ScoringStyle;
import robowiki.runner.ScoreLog.BattleScore;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class RoboRunner {
  private static final String PROPERTIES_FILENAME = "roborunner.properties";
  private static final String DATA_DIR = "data";
  private static final String ROBOCODE_PATHS_PROPERTY = "robocodePaths";
  private static final String JVM_ARGS_PROPERTY = "jvmArgs";
  private static final String BOTS_DIRS_PROPERTY = "botsDirs";
  private static final String DEFAULT_JVM_ARGS = "-Xmx512M";
  private static final String DEFAULT_BOTS_DIRS = "./bots";
  private static final String SLASH = System.getProperty("file.separator");

  private BattleRunner _battleRunner;
  private RunnerConfig _config;
  private boolean _missingBots;

  public static void main(String[] args) {
    args = getCombinedArgs(args);
    String challengerBot = parseStringArgument("bot", args,
        "ERROR: Pass a bot with -bot, eg: -bot voidious.Dookious 1.573c");
    String challengeFile = parseStringArgument("c", args,
        "ERROR: Pass a challenge file with -c, eg: -c challenges" + SLASH
        + "testbed.rrc");
    int seasons = -1;
    try {
      seasons = Integer.parseInt(parseStringArgument("seasons", args,
          "ERROR: Pass number of seasons with -seasons, eg: -seasons 10"));
    } catch (NumberFormatException nfe) {
      // semi-expected
    }
    int threads = -1;
    String threadsArg = parseStringArgument("t", args);
    if (threadsArg != null) {
      try {
        threads = Integer.parseInt(threadsArg);
      } catch (NumberFormatException nfe) {
        // semi-expected
      }
    }
    boolean forceWikiOutput = parseBooleanArgument("wiki", args);
    if (challengerBot == null || challengeFile == null || seasons == -1) {
      printHelp();
      return;
    }

    RoboRunner runner = new RoboRunner(
        challengerBot, challengeFile, seasons, threads, forceWikiOutput);
    if (runner.isMissingBots()) {
      System.out.println("Aborted due to missing bots.");
      System.out.println();
    } else {
      runner.runBattles();
      runner.shutdown();
    }
  }

  private static void printHelp() {
    PrintStream out = System.out;
    out.println();
    out.println("Usage: rr.sh -bot package.BotName 1.2.3 "
        + "-c challenge.rrc -seasons 25");
    out.println();
    out.println("Runs the challenger bot (-bot) against the challenge");
    out.println("specified in the .rrc file (-c), iterating over the");
    out.println("specified number of seasons (-seasons). Run 0 seasons to see");
    out.println("challenge scores without running any battles.");
    out.println();
    out.println("Robocode installs are specified in roborunner.properties.");
    out.println("By default, one thread is used for each install. JARs");
    out.println("missing from the robots" + SLASH + " directories will be");
    out.println("copied over from ." + SLASH + "bots" + SLASH + ", if"
        + "present.");
    out.println();
    out.println("Format of the .rrc file is:");
    out.println("------");
    out.println("<Challenge name>");
    out.println("{PERCENT_SCORE|SURVIVAL_FIRSTS|SURVIVAL_SCORE|BULLET_DAMAGE|"
        + "MOVEMENT_CHALLENGE}");
    out.println("<Number of rounds>");
    out.println("<Battlefield width> (optional)");
    out.println("<Battlefield height> (optional)");
    out.println("Easy Bots {");
    out.println("  package1.Bot1 1.0");
    out.println("  package2.Bot2 1.0");
    out.println("  package3.Bot3 1.0, package4.Bot4 1.0");
    out.println("}");
    out.println("Hard Bots {");
    out.println("  package3.Bot3 1.0");
    out.println("  package4.Bot4 1.0");
    out.println("}");
    out.println("------");
    out.println("You don't need to separate bots into groups, but you can. If");
    out.println("you do and you have more than one group, overall score is");
    out.println("the average of the group scores. Lines with multiple,");
    out.println("comma delimited bots are run as melee battles against all");
    out.println("the bots. The challenger's score is the average pair-wise");
    out.println("score between the challenger and each of the bots.");
    out.println();
    out.println("Things you can configure with optional command line args:");
    out.println("  -t <threads>  -- force number of Robocode processes used");
    out.println("  -wiki         -- force wiki formatted score output");
    out.println();
    out.println("Things you can configure in roborunner.properties:");
    out.println("  robocodePaths=<comma delimited list of Robocode installs>");
    out.println("  jvmArgs=<space delimited list of JVM args to battle "
    		+ "processes>");
    out.println("  botsDirs=<comma delimited list of dirs to look for bot "
        + "JARs>");
    out.println();
    out.println("Guava library should be placed in the lib dir, and rr.sh");
    out.println("must include it in the classpath. Available from:");
    out.println("  http://code.google.com/p/guava-libraries/");
    out.println();
    out.println("Happy Robocoding!");
    out.println();
  }

  public RoboRunner(String challengerBot, String challengeFilePath,
      int seasons, int threads, boolean forceWikiOutput) {
    _config = loadConfig(Preconditions.checkNotNull(challengerBot),
                         Preconditions.checkNotNull(challengeFilePath),
                         seasons, threads, forceWikiOutput);
    if (seasons > 0) {
      _missingBots = false;
      copyBots(_config.botsDirs);
      if (!isMissingBots()) {
        _battleRunner = new BattleRunner(_config.robocodePaths,
            _config.jvmArgs, _config.challenge.rounds,
            _config.challenge.battleFieldWidth,
            _config.challenge.battleFieldHeight);
      }
    }
  }

  private RunnerConfig loadConfig(String challengerBot,
      String challengeFilePath, int seasons, int threads,
      boolean forceWikiOutput) {
    Properties runnerProperties = loadRoboRunnerProperties();
    Iterable<String> pathsIterator = Iterables.transform(
        Lists.newArrayList(runnerProperties.getProperty(ROBOCODE_PATHS_PROPERTY)
            .replaceAll(SLASH + "+", SLASH).split(" *, *")),
        new Function<String, String>() {
          @Override
          public String apply(String input) {
            return input.replaceAll(SLASH + "$", "");
          }
        });

    Set<String> robocodePaths = Sets.newHashSet();
    for (String path : pathsIterator) {
      if (threads > 0 && robocodePaths.size() == threads) {
        break;
      } else {
        robocodePaths.add(path);
      }
    }

    String jvmArgs = runnerProperties.getProperty(JVM_ARGS_PROPERTY);
    List<String> botsDirs = Lists.newArrayList(
        runnerProperties.getProperty(BOTS_DIRS_PROPERTY).trim().split(" *, *"));
    ChallengeConfig challenge = ChallengeConfig.load(challengeFilePath);
    return new RunnerConfig(robocodePaths, jvmArgs, botsDirs, challenge,
        challengerBot, seasons, forceWikiOutput);
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
    if (!runnerProperties.containsKey(JVM_ARGS_PROPERTY)) {
      System.out.println("WARNING: Couldn't find property " + JVM_ARGS_PROPERTY
          + ", setting to default: " + DEFAULT_JVM_ARGS);
      runnerProperties.setProperty(JVM_ARGS_PROPERTY, DEFAULT_JVM_ARGS);
      saveRoboRunnerProperties(runnerProperties);
    }
    if (!runnerProperties.containsKey(BOTS_DIRS_PROPERTY)) {
      System.out.println("WARNING: Couldn't find property " + BOTS_DIRS_PROPERTY
          + ", setting to default: " + DEFAULT_BOTS_DIRS);
      runnerProperties.setProperty(BOTS_DIRS_PROPERTY, DEFAULT_BOTS_DIRS);
      saveRoboRunnerProperties(runnerProperties);
    }
    return runnerProperties;
  }

  private void writeDefaultProperties() {
    Properties defaultProperties = new Properties();
    defaultProperties.setProperty(ROBOCODE_PATHS_PROPERTY,
        "/home/pez/robocode_1740_1, /home/pez/robocode_1740_2");
    defaultProperties.setProperty(JVM_ARGS_PROPERTY, DEFAULT_JVM_ARGS);
    defaultProperties.setProperty(BOTS_DIRS_PROPERTY, DEFAULT_BOTS_DIRS);
    saveRoboRunnerProperties(defaultProperties);
  }

  private void saveRoboRunnerProperties(Properties runnerProperties) {
    try {
      runnerProperties.store(new FileOutputStream(PROPERTIES_FILENAME), null);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void copyBots(List<String> botsDirs) {
    System.out.println();
    System.out.print("Copying missing bots...");
    int jarsCopied = 0;
    List<BotList> allBots =
        Lists.newArrayList(new BotList(_config.challengerBot));
    allBots.addAll(_config.challenge.allReferenceBots);
    for (BotList botList : allBots) {
      for (String bot : botList.getBotNames()) {
        String botJar = getBotJarName(bot);
        File sourceJar = null;
        for (String botsDir : botsDirs) {
          File potentialSourceJar = new File(botsDir + SLASH + botJar);
          if (potentialSourceJar.exists()) {
            sourceJar = potentialSourceJar;
          }
        }
        for (String path : _config.robocodePaths) {
          File jarFile = new File(path + SLASH + "robots" + SLASH + botJar);
          if (!jarFile.exists()) {
            if (sourceJar == null) {
              if (!_missingBots) {
                System.out.println();
              }
              _missingBots = true;
              System.out.println("ERROR: Can't find " + botJar);
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
    if (!_missingBots) {
      System.out.println(" " + jarsCopied + " JAR copies done!");
    }
  }

  private String getBotJarName(String bot) {
    return bot.replaceAll(" ", "_") + ".jar";
  }

  public boolean isMissingBots() {
    return _missingBots;
  }

  public void runBattles() {
    final ChallengeConfig challenge = _config.challenge;
    final String challenger = _config.challengerBot;
    printRunnerHeaders(challenge, challenger);
    long startTime = System.nanoTime();

    final ScoreLog scoreLog = loadScoreLog(challenger);
    List<BotList> battleSet = getBattleSet(scoreLog, challenge, challenger);
    final ScoringStyle scoringStyle = challenge.scoringStyle;
    final boolean printWikiFormat =
        scoringStyle.isChallenge() || _config.forceWikiOutput;
    final String xmlFilePath = DATA_DIR + SLASH + challenger + ".xml.gz";
    final Map<String, ScoreError> errorMap =
        getScoreErrorMap(scoreLog, scoringStyle, challenger);

    if (_config.seasons > 0) {
      BattleResultHandler resultHandler = new BattleResultHandler() {
        @Override
        public void processResults(
            Map<String, RobotScore> rawScoreMap, long elapsedTime) {
          List<RobotScore> botArrayList =
              Lists.newArrayList(rawScoreMap.values());
          scoreLog.addBattle(botArrayList, challenge.rounds, elapsedTime);
          scoreLog.saveScoreLog(xmlFilePath);

          String botList = scoreLog.getSortedBotListFromScores(botArrayList);
          BattleScore lastScore = scoreLog.getLastBattleScore(botList);
          BattleScore avgScore = scoreLog.getAverageBattleScore(botList);
          errorMap.put(botList,
              getScoreError(scoreLog, scoringStyle, challenger, botList));

          printBattleScore(challenger, botList, lastScore, avgScore,
              scoringStyle, elapsedTime);
          if (rawScoreMap.size() > 2) {
            printMeleeScores(lastScore, avgScore, challenger, scoringStyle);
          }
          printOverallScores(
              scoreLog, challenger, challenge, printWikiFormat);
        }
      };
      _battleRunner.runBattles(battleSet, resultHandler);
      System.out.println();
      System.out.println("Done! Took "
          + formatBattleTime(System.nanoTime() - startTime));
      System.out.println();
    }

    printAllScores(scoreLog, challenge);
    System.out.println();
    printOverallScores(scoreLog, challenger, challenge, printWikiFormat);
    System.out.println();
  }

  private void printRunnerHeaders(
      ChallengeConfig challenge, String challenger) {
    System.out.println();
    System.out.println("Challenger: " + challenger);
    System.out.println("Challenge:  " + challenge.name);
    System.out.println("Seasons:    " + _config.seasons);
    System.out.println("Threads:    " + _config.robocodePaths.size());
    System.out.println("Scoring:    "
        + challenge.scoringStyle.getDescription());
    System.out.println();
  }

  private List<BotList> getBattleSet(
      ScoreLog scoreLog, ChallengeConfig challenge, String challenger) {
    Map<String, Integer> skipMap = getSkipMap(scoreLog);
    List<BotList> battleSet = Lists.newArrayList();
    for (int x = 0; x < _config.seasons; x++) {
      for (BotList botList : challenge.allReferenceBots) {
        List<String> battleBots = Lists.newArrayList(challenger);
        List<String> botNames = botList.getBotNames();
        if (!skip(skipMap, scoreLog.getSortedBotList(botNames))) {
          battleBots.addAll(botNames);
          battleSet.add(new BotList(battleBots));
        }
      }
    }
    return battleSet;
  }

  private Map<String, Integer> getSkipMap(ScoreLog scoreLog) {
    Map<String, Integer> skipMap = Maps.newHashMap();
    for (String botList : scoreLog.getBotLists()) {
      int numBattles = scoreLog.getBattleScores(botList).size();
      skipMap.put(botList, numBattles);
    }
    return skipMap;
  }

  private boolean skip(Map<String, Integer> skipMap, String sortedBotList) {
    if (skipMap.containsKey(sortedBotList)) {
      int skipsLeft = skipMap.get(sortedBotList);
      if (skipsLeft > 0) {
        skipMap.put(sortedBotList, skipsLeft - 1);
        return true;
      }
    }
    return false;
  }

  private Map<String, ScoreError> getScoreErrorMap(
      ScoreLog scoreLog, ScoringStyle scoringStyle, String challenger) {
    Map<String, ScoreError> errorMap = Maps.newHashMap();
    for (String botList : scoreLog.getBotLists()) {
      errorMap.put(botList,
          getScoreError(scoreLog, scoringStyle, challenger, botList));
    }
    return errorMap;
  }

  private ScoreError getScoreError(ScoreLog scoreLog,
      ScoringStyle scoringStyle, String challenger, String botList) {
    List<BattleScore> battleScores = scoreLog.getBattleScores(botList);
    List<Double> scores = Lists.newArrayList();
    for (BattleScore battleScore : battleScores) {
      RobotScore totalScore = battleScore.getRelativeTotalScore(challenger);
      scores.add(scoringStyle.getScore(totalScore));
    }
    return new ScoreError(standardDeviation(scores), scores.size(),
        scoreLog.getAverageBattleScore(botList).getElapsedTime());
  }

  private double standardDeviation(List<Double> values) {
    double avg = average(values);
    double sumSquares = 0;
    for (double value : values) {
      sumSquares += square(avg - value);
    }
    return Math.sqrt(sumSquares / values.size());
  }

  private double square(double d) {
    return d * d;
  }

  private double average(List<Double> values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return (sum / values.size());
  }

  private ScoreLog loadScoreLog(String challengerBot) {
    String filePath = DATA_DIR + SLASH + challengerBot + ".xml.gz";
    File dataFile = new File(filePath);
    if (dataFile.exists()) {
      try {
        return ScoreLog.loadScoreLog(filePath);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (XMLStreamException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return new ScoreLog(challengerBot);
  }

  private void printBattleScore(String challenger, String botList,
      BattleScore lastScore, BattleScore avgScore, ScoringStyle scoringStyle,
      long elapsedTime) {
    System.out.println("  " + challenger + " vs " +
        botList.replace(",", ", ") + ": "
        + round(scoringStyle.getScore(
            lastScore.getRelativeTotalScore(challenger)), 2)
        + ", took " + formatBattleTime(elapsedTime) + ", avg: "
        + round(scoringStyle.getScore(
            avgScore.getRelativeTotalScore(challenger)), 2));
  }

  private void printOverallScores(ScoreLog scoreLog,
      String challenger, ChallengeConfig challenge, boolean printWikiFormat) {
    ScoringStyle scoringStyle = challenge.scoringStyle;
    ScoreSummary scoreSummary = getScoreSummary(
        scoreLog, challenge.allReferenceBots, scoringStyle);
    int challengeBotLists = challenge.allReferenceBots.size();
    double numSeasons =
        round(((double) scoreSummary.numBattles) / challengeBotLists, 2);
    double overallScore;
    StringBuilder groupScores = new StringBuilder();
    StringBuilder wikiScores = new StringBuilder();
    wikiScores.append("| [[").append(
            challenger.replaceAll("^[^ ]*\\.", "").replace(" ", "]] "))
        .append(" || [[User:Author|Author]] || Type || ");
    if (challenge.hasGroups()) {
      double sumGroups = 0;
      int scoredGroups = 0;
      for (BotListGroup group : challenge.referenceBotGroups) {
        if (group.referenceBots.size() > 1) {
          for (BotList botList : group.referenceBots) {
            double wikiScore = getWikiScore(scoreLog, botList, scoringStyle);
            wikiScores.append(wikiScore).append(" || ");
          }
        }
        ScoreSummary summary = getScoreSummary(
            scoreLog, group.referenceBots, scoringStyle);
        double groupScore = summary.getTotalScore();
        groupScores.append("  ").append(group.name).append(": ")
            .append(groupScore).append("\n");
        wikiScores.append("'''").append(groupScore).append("''' || ");
        if (summary.scoredBotLists > 0) {
          sumGroups += groupScore;
          scoredGroups++;
        }
      }
      ScoreSummary overallSummary =
          new ScoreSummary(sumGroups, scoredGroups, scoredGroups);
      overallScore = overallSummary.getTotalScore();
    } else {
      if (printWikiFormat) {
        for (BotList botList : challenge.allReferenceBots) {
          double wikiScore = getWikiScore(scoreLog, botList, scoringStyle);
          wikiScores.append(wikiScore).append(" || ");
        }
      }
      overallScore = scoreSummary.getTotalScore();
    }

    String botsFaced = "";
    if (scoreSummary.scoredBotLists < challenge.allReferenceBots.size()) {
      double percentBotsFaced = 100 * ((double) scoreSummary.scoredBotLists)
          / challenge.allReferenceBots.size();
      botsFaced = "  (" + round(Math.min(percentBotsFaced, 99.9), 1)
          + "% bots faced)";
    }
    System.out.println("Overall score: " + overallScore
        + ", " + numSeasons + " seasons" + botsFaced);
    wikiScores.append("'''").append(overallScore).append("''' || ");
    wikiScores.append(numSeasons).append(" seasons");
    if (groupScores.length() > 0) {
      System.out.print(groupScores.toString());
    }
    if (printWikiFormat) {
      System.out.println("Wiki format: " + wikiScores.toString());
      System.out.println();
    }
  }

  private ScoreSummary getScoreSummary(ScoreLog scoreLog,
      List<BotList> referenceBots, ScoringStyle scoringStyle) {
    double sumScores = 0;
    int numBattles = 0;
    int scoredBotLists = 0;
    for (BotList botList : referenceBots) {
      String botListString = scoreLog.getSortedBotList(botList.getBotNames());
      if (scoreLog.hasBotList(botListString)) {
        RobotScore totalRobotScore = scoreLog
            .getAverageBattleScore(botListString)
            .getRelativeTotalScore(scoreLog.challenger);
        sumScores += scoringStyle.getScore(totalRobotScore);
        scoredBotLists++;
        numBattles += totalRobotScore.numBattles;
      }
    }
    return new ScoreSummary(sumScores, numBattles, scoredBotLists);
  }

  private double getWikiScore(
      ScoreLog scoreLog, BotList botList, ScoringStyle scoringStyle) {
    String botListString = scoreLog.getSortedBotList(botList.getBotNames());
    double score = -1;
    if (scoreLog.hasBotList(botListString)) {
      RobotScore totalRobotScore = scoreLog
          .getAverageBattleScore(botListString)
          .getRelativeTotalScore(scoreLog.challenger);
      score = round(scoringStyle.getScore(totalRobotScore), 2);
    }
    return score;
  }

  private void printAllScores(ScoreLog scoreLog, ChallengeConfig challenge) {
    System.out.println("All scores:");
    for (BotList botList : challenge.allReferenceBots) {
      String botListString = scoreLog.getSortedBotList(botList.getBotNames());
      if (scoreLog.hasBotList(botListString)) {
        RobotScore totalRobotScore = scoreLog
            .getAverageBattleScore(botListString)
            .getRelativeTotalScore(scoreLog.challenger);
        System.out.println("  " + botListString + ": "
            + round(challenge.scoringStyle.getScore(totalRobotScore), 2));
      }
    }
  }

  private void printMeleeScores(BattleScore lastScore, BattleScore avgScore,
      String challenger, ScoringStyle scoringStyle) {
    for (String enemy : lastScore.getBots()) {
      if (!enemy.equals(challenger)) {
        // TODO: show raw score fields too
        System.out.println("    vs " + enemy + ": "
            + round(scoringStyle.getScore(
                lastScore.getRelativeScore(challenger, enemy)), 2)
            + ", avg: "
            + round(scoringStyle.getScore(
                avgScore.getRelativeScore(challenger, enemy)), 2));
      }
    }
  }

  private String formatBattleTime(long nanoTime) {
    return Double.toString(round((double) nanoTime / 1000000000, 1)) + "s";
  }

  public void shutdown() {
    if (_battleRunner != null) {
      _battleRunner.shutdown();
    }
  }

  private static class RunnerConfig {
    public final Set<String> robocodePaths;
    public final String jvmArgs;
    public final List<String> botsDirs;
    public final ChallengeConfig challenge;
    public final String challengerBot;
    public final int seasons;
    public final boolean forceWikiOutput;

    public RunnerConfig(Set<String> robocodePaths, String jvmArgs,
        List<String> botsDirs, ChallengeConfig challenge, String challengerBot,
        int seasons, boolean forceWikiOutput) {
      this.robocodePaths = Preconditions.checkNotNull(robocodePaths);
      this.jvmArgs = Preconditions.checkNotNull(jvmArgs);
      this.botsDirs = Preconditions.checkNotNull(botsDirs);
      this.challenge = Preconditions.checkNotNull(challenge);
      this.challengerBot = Preconditions.checkNotNull(challengerBot);
      this.seasons = seasons;
      this.forceWikiOutput = forceWikiOutput;
    }
  }

  private static class ScoreSummary {
    public final double sumScores;
    public final int numBattles;
    public final int scoredBotLists;

    public ScoreSummary(
        double sumScores, int numBattles, int scoredBotLists) {
      this.sumScores = sumScores;
      this.numBattles = numBattles;
      this.scoredBotLists = scoredBotLists;
    }

    public double getTotalScore() {
      return round(sumScores / scoredBotLists, 2);
    }
  }
}
