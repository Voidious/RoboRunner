package robowiki.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import robowiki.runner.BattleRunner.BotSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class RoboRunner {
  private static final String PROPERTIES_FILENAME = "roborunner.properties";
  private BattleRunner _battleRunner;
  private RunnerConfig _config;

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
    ChallengeConfig challenge = loadChallengeConfig(challengeFilePath);
    return new RunnerConfig(robocodePaths, challenge, challengerBot, seasons);
  }

  private ChallengeConfig loadChallengeConfig(String challengeFilePath) {
    try {
      // TODO: grouping like RoboResearch
      List<String> fileLines = Files.readLines(
          new File(challengeFilePath), Charset.defaultCharset());
      String name = fileLines.get(0);
      ScoringStyle scoringStyle = ScoringStyle.parseStyle(fileLines.get(1));
      int rounds = Integer.parseInt(fileLines.get(2));
      Set<BotSet> referenceBots = Sets.newHashSet();

      Integer width = null;
      Integer height = null;
      for (int x = 3; x < fileLines.size(); x++) {
        String line = fileLines.get(x).trim();
        if (line.matches("^\\d+$")) {
          int value = Integer.parseInt(line);
          if (width == null) {
            width = value;
          } else if (height == null) {
            height = value;
          }
        } else if (line.length() > 0 && !line.contains("{")
            && !line.contains("}") && !line.contains("#")) {
          referenceBots.add(
              new BotSet(Lists.newArrayList(line.split(" *, *"))));
        }
      }

      return new ChallengeConfig(name, rounds, scoringStyle,
          (width == null ? 800 : width), (height == null ? 600 : height),
          referenceBots);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
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
    // TODO: count battles already run for each bot, put in hash map,
    //       skip corresponding battles when loading up battle set
    List<BotSet> battleSet = Lists.newArrayList();
    for (int x = 0; x < _config.seasons; x++) {
      for (BotSet botSet : _config.challenge.referenceBots) {
        List<String> battleBots = Lists.newArrayList(_config.challengerBot);
        battleBots.addAll(botSet.getBotNames());
        battleSet.add(new BotSet(battleBots));
      }
    }
    _battleRunner.runBattles(battleSet);
  }

  public void shutdown() {
    _battleRunner.shutdown();
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

  private static class ChallengeConfig {
    public final String name;
    public final int rounds;
    public final ScoringStyle scoringStyle;
    public final int battleFieldWidth;
    public final int battleFieldHeight;
    public final Set<BotSet> referenceBots;

    public ChallengeConfig(String name, int rounds, ScoringStyle scoringStyle,
        int battleFieldWidth, int battleFieldHeight,
        Set<BotSet> referenceBots) {
      this.name = name;
      this.rounds = rounds;
      this.scoringStyle = scoringStyle;
      this.battleFieldWidth = battleFieldWidth;
      this.battleFieldHeight = battleFieldHeight;
      this.referenceBots = referenceBots;
    }
  }

  private enum ScoringStyle {
    PERCENT_SCORE,
    BULLET_DAMAGE;

    public static ScoringStyle parseStyle(String styleString) {
      if (styleString.contains("PERCENT_SCORE")) {
        return PERCENT_SCORE;
      } else if (styleString.contains("BULLET_DAMAGE")) {
        return BULLET_DAMAGE;
      } else {
        throw new IllegalArgumentException(
            "Unrecognized scoring style: " + styleString);
      }
    }
  }
}
