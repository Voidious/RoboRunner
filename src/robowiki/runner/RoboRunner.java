package robowiki.runner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import robowiki.runner.BattleRunner.BotSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
}
