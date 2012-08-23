package robowiki.runner;

import static robowiki.runner.RunnerUtil.getCombinedArgs;
import static robowiki.runner.RunnerUtil.parseBooleanArgument;
import static robowiki.runner.RunnerUtil.parseStringArgument;
import static robowiki.runner.RunnerUtil.round;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import robowiki.runner.BattleRunner.BattleResultHandler;
import robowiki.runner.BattleRunner.BattleSelector;
import robowiki.runner.ChallengeConfig.BotListGroup;
import robowiki.runner.RobotScore.ScoringStyle;
import robowiki.runner.ScoreLog.BattleScore;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
  private static final double SMART_BATTLE_RANDOM_RATE = 0.1;

  private BattleRunner _battleRunner;
  private RunnerConfig _config;
  private boolean _missingBots;
  private List<String> _runningBotLists;

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
    boolean smartBattles = parseBooleanArgument("smart", args);
    if (challengerBot == null || challengeFile == null || seasons == -1) {
      printHelp();
      return;
    }

    RoboRunner runner = new RoboRunner(challengerBot, challengeFile, seasons,
        threads, forceWikiOutput, smartBattles);
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
    out.println("  -t <threads> -- force number of Robocode processes used");
    out.println("  -wiki        -- force wiki formatted score output");
    out.println("  -smart       -- smart battle selection to get accurate "
        + "overall score");
    out.println("                  as quickly as possible");
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
      int seasons, int threads, boolean forceWikiOutput, boolean smartBattles) {
    _config = loadConfig(Preconditions.checkNotNull(challengerBot),
                         Preconditions.checkNotNull(challengeFilePath),
                         seasons, threads, forceWikiOutput, smartBattles);
    if (seasons > 0) {
      _missingBots = false;
      _runningBotLists = Lists.newArrayList();
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
      boolean forceWikiOutput, boolean smartBattles) {
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
        challengerBot, seasons, forceWikiOutput, smartBattles);
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

    final String xmlFilePath = DATA_DIR + SLASH + challenger + ".xml.gz";
    final ScoreLog scoreLog = loadScoreLog(challenger, xmlFilePath);
    final ScoringStyle scoringStyle = challenge.scoringStyle;
    final boolean printWikiFormat =
        scoringStyle.isChallenge() || _config.forceWikiOutput;
    final Map<String, ScoreError> errorMap =
        getScoreErrorMap(scoreLog, scoringStyle, challenger);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        scoreLog.saveScoreLog(xmlFilePath);
      }
    });

    if (_config.seasons > 0) {
      BattleResultHandler resultHandler = newBattleResultHandler(scoreLog,
          challenge, challenger, xmlFilePath, errorMap, printWikiFormat);
      if (_config.smartBattles) {
        BattleSelector battleSelector = newBattleSelector(
            getBattleList(scoreLog, challenge, challenger, 2), challenge,
            challenger, errorMap);
        int numBattles =
            (_config.seasons) * (_config.challenge.allReferenceBots.size())
                - scoreLog.getBattleCount(_config.challenge.allReferenceBots);
        _battleRunner.runBattles(battleSelector, resultHandler, numBattles);
      } else {
        _battleRunner.runBattles(
            getBattleList(scoreLog, challenge, challenger), resultHandler);
      }
      System.out.println();
      System.out.println("Done! Took "
          + formatBattleTime(System.nanoTime() - startTime));
      System.out.println();
    }

    printAllScores(scoreLog, challenge, errorMap);
    System.out.println();
    printOverallScores(
        scoreLog, errorMap, challenger, challenge, printWikiFormat, true);
    System.out.println();
  }

  private void printRunnerHeaders(
      ChallengeConfig challenge, String challenger) {
    System.out.println();
    System.out.println("Challenger:     " + challenger);
    System.out.println("Challenge:      " + challenge.name);
    System.out.println("Seasons:        " + _config.seasons);
    System.out.println("Threads:        " + _config.robocodePaths.size());
    System.out.println("Scoring:        "
        + challenge.scoringStyle.getDescription());
    System.out.println("Smart battles:  "
        + (_config.smartBattles ? "On" : "Off"));
    System.out.println();
  }

  private List<BotList> getBattleList(
      ScoreLog scoreLog, ChallengeConfig challenge, String challenger) {
    return getBattleList(scoreLog, challenge, challenger, _config.seasons);
  }

  private List<BotList> getBattleList(ScoreLog scoreLog,
      ChallengeConfig challenge, String challenger, int seasons) {
    Map<String, Integer> skipMap = getSkipMap(scoreLog);
    List<BotList> battleList = Lists.newArrayList();
    for (int x = 0; x < seasons; x++) {
      for (BotList botList : challenge.allReferenceBots) {
        List<String> battleBots = Lists.newArrayList(challenger);
        List<String> botNames = botList.getBotNames();
        if (!skip(skipMap, scoreLog.getSortedBotList(botNames))) {
          battleBots.addAll(botNames);
          battleList.add(new BotList(battleBots));
        }
      }
    }
    return ImmutableList.copyOf(battleList);
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
    return new ScoreError(scores,
        scoreLog.getAverageBattleScore(botList).getElapsedTime());
  }

  private ScoreLog loadScoreLog(String challengerBot, String filePath) {
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
      long elapsedTime, Map<String, ScoreError> errorMap) {
    ScoreError scoreError = errorMap.get(botList);
    System.out.println("  " + challenger + " vs " +
        botList.replace(",", ", ") + ": "
        + round(scoringStyle.getScore(
            lastScore.getRelativeTotalScore(challenger)), 2)
        + ", took " + formatBattleTime(elapsedTime));
    if (scoreError.numBattles > 1) {
      System.out.println("    Average: "
          + round(scoringStyle.getScore(
              avgScore.getRelativeTotalScore(challenger)), 2)
          + "  +- " + round(1.96 * scoreError.getStandardError(), 2)
          + "  (" +  scoreError.numBattles + " battles)");
    }
  }

  private void printOverallScores(ScoreLog scoreLog,
      Map<String, ScoreError> errorMap, String challenger,
      ChallengeConfig challenge, boolean printWikiFormat, boolean finalScore) {
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

    boolean showConfidence = (getMinBattles(errorMap) >= 2);
    int confidenceIterations =
        (finalScore ? Math.min(20000, 10000000 / scoreSummary.numBattles)
                    : Math.min(1000, 100000 / scoreSummary.numBattles));
    double confidence = 0;
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
      if (showConfidence) {
        confidence = getGroupsConfidence(scoreLog, challenge.referenceBotGroups,
            errorMap, confidenceIterations);
      }
    } else {
      if (printWikiFormat) {
        for (BotList botList : challenge.allReferenceBots) {
          double wikiScore = getWikiScore(scoreLog, botList, scoringStyle);
          wikiScores.append(wikiScore).append(" || ");
        }
      }
      overallScore = scoreSummary.getTotalScore();
      if (showConfidence) {
        confidence = getOverallConfidence(scoreLog, challenge.allReferenceBots,
            errorMap, confidenceIterations);
      }
    }

    String botsFaced = "";
    if (scoreSummary.scoredBotLists < challenge.allReferenceBots.size()) {
      double percentBotsFaced = 100 * ((double) scoreSummary.scoredBotLists)
          / challenge.allReferenceBots.size();
      botsFaced = "  (" + round(Math.min(percentBotsFaced, 99.9), 1)
          + "% bots faced)";
    }
    System.out.println("Overall score: " + overallScore
        + (showConfidence ? "  +- " + round(confidence, 2) : "")
        + "  (" + numSeasons + " seasons)" + botsFaced);
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

  private double getOverallConfidence(ScoreLog scoreLog, List<BotList> botLists,
      Map<String, ScoreError> errorMap, int iterations) {
    List<Double> overallScores = Lists.newArrayList();
    for (int x = 0; x < iterations; x++) {
      overallScores.add(
          generateOverallScore(scoreLog, botLists, errorMap));
    }
    return 1.96 * RunnerUtil.standardDeviation(overallScores);
  }

  private double generateOverallScore(ScoreLog scoreLog, List<BotList> botLists,
      Map<String, ScoreError> errorMap) {
    double overallTotal = 0;
    int numScores = 0;
    for (BotList botList : botLists) {
      String botListString = scoreLog.getSortedBotList(botList.getBotNames());
      if (errorMap.containsKey(botListString)) {
        ScoreError botError = errorMap.get(botListString);
        overallTotal += botError.generateRandomAverageScore();
        numScores++;
      }
    }
    return overallTotal / numScores;
  }

  private double getGroupsConfidence(ScoreLog scoreLog,
      List<BotListGroup> botListGroups, Map<String, ScoreError> errorMap,
      int iterations) {
    List<Double> overallScores = Lists.newArrayList();
    for (int x = 0; x < iterations; x++) {
      overallScores.add(
          generateGroupsScore(scoreLog, botListGroups, errorMap));
    }
    return 1.96 * RunnerUtil.standardDeviation(overallScores);
  }


  private double generateGroupsScore(ScoreLog scoreLog,
      List<BotListGroup> botListGroups, Map<String, ScoreError> errorMap) {
    double overallTotal = 0;
    int numGroupScores = 0;
    for (BotListGroup group : botListGroups) {
      double groupTotal = 0;
      int numBotScores = 0;
      for (BotList botList : group.referenceBots) {
        String botListString = scoreLog.getSortedBotList(botList.getBotNames());
        if (errorMap.containsKey(botListString)) {
          ScoreError botError = errorMap.get(botListString);
          groupTotal += botError.generateRandomAverageScore();
          numBotScores++;
        }
      }
      if (numBotScores > 0) {
        overallTotal += groupTotal / numBotScores;
        numGroupScores++;
      }
    }
    return overallTotal / numGroupScores;
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

  private void printAllScores(ScoreLog scoreLog, ChallengeConfig challenge,
      Map<String, ScoreError> errorMap) {
    System.out.println("All scores:");
    for (BotList botList : challenge.allReferenceBots) {
      String botListString = scoreLog.getSortedBotList(botList.getBotNames());
      if (scoreLog.hasBotList(botListString)) {
        RobotScore totalRobotScore = scoreLog
            .getAverageBattleScore(botListString)
            .getRelativeTotalScore(scoreLog.challenger);
        ScoreError scoreError = errorMap.get(botListString);
        System.out.println("  " + botListString + ": "
            + round(challenge.scoringStyle.getScore(totalRobotScore), 2)
            + (scoreError.numBattles > 1
                ? "  +- " + round(1.96 * scoreError.getStandardError(), 2) : "")
            + "  (" +  scoreError.numBattles + " battles)");
      }
    }
  }

  private void printMeleeScores(BattleScore lastScore, BattleScore avgScore,
      String challenger, ScoringStyle scoringStyle) {
    RobotScore challengerScore = lastScore.getRobotScore(challenger);
    RobotScore avgChallengerScore = avgScore.getRobotScore(challenger);

    Map<RobotScore, RobotScore> avgScoreMap = Maps.newHashMap();
    List<RobotScore> avgRobotScores =
        Lists.newArrayList(avgScore.getRobotScores());
    avgRobotScores.remove(avgChallengerScore);
    for (RobotScore robotScore : lastScore.getRobotScores()) {
      if (robotScore != challengerScore) {
        Iterator<RobotScore> avgRobotScoreIterator = avgRobotScores.iterator();
        while (avgRobotScoreIterator.hasNext()) {
          RobotScore avgRobotScore = avgRobotScoreIterator.next();
          if (robotScore.botName.equals(avgRobotScore.botName)) {
            avgScoreMap.put(robotScore, avgRobotScore);
            avgRobotScoreIterator.remove();
            break;
          }
        }
      }
    }
    for (RobotScore robotScore : lastScore.getRobotScores()) {
      if (robotScore != challengerScore) {
        RobotScore relativeScore = challengerScore.getScoreRelativeTo(
            robotScore, lastScore.getNumRounds());
        RobotScore avgRelativeScore = avgChallengerScore.getScoreRelativeTo(
            avgScoreMap.get(robotScore), avgScore.getNumRounds());
        System.out.println("    vs " + robotScore.botName + ": "
            + round(scoringStyle.getScore(relativeScore), 2)
            + ", avg: "
            + round(scoringStyle.getScore(avgRelativeScore), 2));
      }
    }
  }

  private String formatBattleTime(long battleTime) {
    return Double.toString(round((double) battleTime / 1000000000, 1)) + "s";
  }

  public void shutdown() {
    if (_battleRunner != null) {
      _battleRunner.shutdown();
    }
  }

  private int getMinBattles(Map<String, ScoreError> errorMap) {
    int minBattles = Integer.MAX_VALUE;
    for (ScoreError scoreError : errorMap.values()) {
      minBattles = Math.min(minBattles, scoreError.numBattles);
    }
    return minBattles;
  }

  private double power(double d, int exp) {
    double r = 1;
    for (int x = 0; x < exp; x++) {
      r *= d;
    }
    return r;
  }

  private BattleResultHandler newBattleResultHandler(final ScoreLog scoreLog,
      final ChallengeConfig challenge, final String challenger,
      final String xmlFilePath, final Map<String, ScoreError> errorMap,
      final boolean printWikiFormat) {
    final ScoringStyle scoringStyle = challenge.scoringStyle;
    return new BattleResultHandler() {
      @Override
      public void processResults(
          List<RobotScore> robotScores, long elapsedTime) {
        scoreLog.addBattle(robotScores, challenge.rounds, elapsedTime);
        scoreLog.saveScoreLog(xmlFilePath);

        String botList = scoreLog.getSortedBotListFromScores(robotScores);
        BattleScore lastScore = scoreLog.getLastBattleScore(botList);
        BattleScore avgScore = scoreLog.getAverageBattleScore(botList);
        errorMap.put(botList,
            getScoreError(scoreLog, scoringStyle, challenger, botList));

        printBattleScore(challenger, botList, lastScore, avgScore,
            scoringStyle, elapsedTime, errorMap);
        if (robotScores.size() > 2) {
          printMeleeScores(lastScore, avgScore, challenger, scoringStyle);
        }
        printOverallScores(
            scoreLog, errorMap, challenger, challenge, printWikiFormat, false);
        _runningBotLists.remove(botList);
      }
    };
  }

  private BattleSelector newBattleSelector(List<BotList> initialBattles,
      final ChallengeConfig challenge, final String challenger,
      final Map<String, ScoreError> errorMap) {
    final LinkedList<BotList> battleList = Lists.newLinkedList(initialBattles);
    return new BattleSelector() {
      @Override
      public BotList nextBotList() {
        if (!battleList.isEmpty()) {
          return battleList.remove();
        };

        int minBattles = getMinBattles(errorMap);
        double randomBattleChance =
            SMART_BATTLE_RANDOM_RATE / power(2, minBattles - 2);
        String nextBotListString = null;
        if (Math.random() < randomBattleChance) {
          List<String> minBotLists = Lists.newArrayList();
          for (Map.Entry<String, ScoreError> entry : errorMap.entrySet()) {
            if (entry.getValue().numBattles == minBattles) {
              minBotLists.add(entry.getKey());
            }
          }
          nextBotListString =
              minBotLists.get((int) Math.random() * minBotLists.size());
        } else {
          double bestGain = Double.NEGATIVE_INFINITY;
          for (Map.Entry<String, ScoreError> entry : errorMap.entrySet()) {
            String botListString = entry.getKey();
            if (challenge.allReferenceBots.size() <= _config.threads
                || !_runningBotLists.contains(botListString)) {
              double accuracyGain = entry.getValue().getAccuracyGainRate();
              if (accuracyGain > bestGain) {
                bestGain = accuracyGain;
                nextBotListString = botListString;
              }
            }
          }
        }

        if (nextBotListString == null) {
          throw new RuntimeException("Failed to select a battle!");
        }
        _runningBotLists.add(nextBotListString);
        List<String> nextBotList =
            Lists.newArrayList(nextBotListString.split(","));
        nextBotList.add(challenger);
        return new BotList(nextBotList);
      }
    };
  }

  private static class RunnerConfig {
    public final Set<String> robocodePaths;
    public final String jvmArgs;
    public final List<String> botsDirs;
    public final ChallengeConfig challenge;
    public final String challengerBot;
    public final int seasons;
    public final boolean forceWikiOutput;
    public final boolean smartBattles;
    public final int threads;

    public RunnerConfig(Set<String> robocodePaths, String jvmArgs,
        List<String> botsDirs, ChallengeConfig challenge, String challengerBot,
        int seasons, boolean forceWikiOutput, boolean smartBattles) {
      this.robocodePaths = Preconditions.checkNotNull(robocodePaths);
      this.jvmArgs = Preconditions.checkNotNull(jvmArgs);
      this.botsDirs = Preconditions.checkNotNull(botsDirs);
      this.challenge = Preconditions.checkNotNull(challenge);
      this.challengerBot = Preconditions.checkNotNull(challengerBot);
      this.seasons = seasons;
      this.forceWikiOutput = forceWikiOutput;
      this.smartBattles = smartBattles;
      this.threads = robocodePaths.size();
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
