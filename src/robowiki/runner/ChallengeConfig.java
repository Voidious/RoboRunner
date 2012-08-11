package robowiki.runner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import robowiki.runner.RobotScore.ScoringStyle;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class ChallengeConfig {
  public static final String DEFAULT_GROUP = "";

  public final String name;
  public final int rounds;
  public final ScoringStyle scoringStyle;
  public final int battleFieldWidth;
  public final int battleFieldHeight;
  public final List<BotListGroup> referenceBotGroups;
  public final List<BotList> allReferenceBots;

  public ChallengeConfig(String name, int rounds, ScoringStyle scoringStyle,
      int battleFieldWidth, int battleFieldHeight,
      List<BotListGroup> referenceBotGroups) {
    this.name = name;
    this.rounds = rounds;
    this.scoringStyle = scoringStyle;
    this.battleFieldWidth = battleFieldWidth;
    this.battleFieldHeight = battleFieldHeight;
    this.referenceBotGroups = referenceBotGroups;
    this.allReferenceBots = getAllReferenceBots();
  }

  private List<BotList> getAllReferenceBots() {
    List<BotList> referenceBots = Lists.newArrayList();
    for (BotListGroup group : referenceBotGroups) {
      referenceBots.addAll(group.referenceBots);
    }
    return referenceBots;
  }

  public boolean hasGroups() {
    return referenceBotGroups.size() > 1;
  }

  public static ChallengeConfig load(String challengeFilePath) {
    try {
      List<String> fileLines = Files.readLines(
          new File(challengeFilePath), Charset.defaultCharset());
      String name = fileLines.get(0);
      ScoringStyle scoringStyle =
          ScoringStyle.parseStyle(fileLines.get(1).trim());
      int rounds = Integer.parseInt(
          fileLines.get(2).toLowerCase().replaceAll("rounds", "").trim());
      List<BotListGroup> botGroups = Lists.newArrayList();
      List<BotList> groupBots = Lists.newArrayList();
      String groupName = DEFAULT_GROUP;

      Integer width = null;
      Integer height = null;
      int maxBots = 1;
      for (int x = 3; x < fileLines.size(); x++) {
        String line = fileLines.get(x).trim();
        if (line.matches("^\\d+$")) {
          int value = Integer.parseInt(line);
          if (width == null) {
            width = value;
          } else if (height == null) {
            height = value;
          }
        } else if (line.length() > 0 && !line.contains("#")) {
          if (line.contains("{")) {
            groupName = line.replace("{", "").trim();
          } else if (line.contains("}")) {
            botGroups.add(new BotListGroup(groupName, groupBots));
            groupName = DEFAULT_GROUP;
            groupBots = Lists.newArrayList();
          } else {
            List<String> botList = Lists.newArrayList(line.split(" *, *"));
            Iterables.removeIf(botList, new Predicate<String>() {
              @Override
              public boolean apply(String botName) {
                if (botName.contains(".") && botName.contains(" ")) {
                  return false;
                } else {
                  System.out.println("WARNING: " + botName + " doesn't look "
                      + "like a bot name, ignoring.");
                  return true;
                }
              }
            });
            maxBots = Math.max(maxBots, 1 + botList.size());
            groupBots.add(new BotList(botList));
          }
        }
      }

      if (scoringStyle == ScoringStyle.MOVEMENT_CHALLENGE && maxBots > 2) {
        throw new RuntimeException("Movement Challenge scoring doesn't work "
            + "for battles with more than 2 bots.");
      }

      if (!groupBots.isEmpty()) {
        botGroups.add(new BotListGroup(groupName, groupBots));
      }

      return new ChallengeConfig(name, rounds, scoringStyle,
          (width == null ? 800 : width), (height == null ? 600 : height),
          botGroups);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static class BotListGroup {
    public final String name;
    public final List<BotList> referenceBots;

    public BotListGroup(String name, List<BotList> referenceBots) {
      this.name = name;
      this.referenceBots = referenceBots;
    }
  }
}
