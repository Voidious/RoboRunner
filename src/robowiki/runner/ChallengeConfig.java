package robowiki.runner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

import robowiki.runner.BattleRunner.BotList;

public class ChallengeConfig {
  public final String name;
  public final int rounds;
  public final ScoringStyle scoringStyle;
  public final int battleFieldWidth;
  public final int battleFieldHeight;
  public final List<BotList> referenceBots;

  public ChallengeConfig(String name, int rounds, ScoringStyle scoringStyle,
      int battleFieldWidth, int battleFieldHeight,
      List<BotList> referenceBots) {
    this.name = name;
    this.rounds = rounds;
    this.scoringStyle = scoringStyle;
    this.battleFieldWidth = battleFieldWidth;
    this.battleFieldHeight = battleFieldHeight;
    this.referenceBots = referenceBots;
  }

  public static ChallengeConfig load(String challengeFilePath) {
    try {
      // TODO: handle grouping like RoboResearch, eg for TCRM
      List<String> fileLines = Files.readLines(
          new File(challengeFilePath), Charset.defaultCharset());
      String name = fileLines.get(0);
      ScoringStyle scoringStyle = ScoringStyle.parseStyle(fileLines.get(1));
      int rounds = Integer.parseInt(fileLines.get(2));
      List<BotList> referenceBots = Lists.newArrayList();

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
              new BotList(Lists.newArrayList(line.split(" *, *"))));
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

  public enum ScoringStyle {
    PERCENT_SCORE,
    SURVIVAL_FIRSTS,
    SURVIVAL_SCORE,
    BULLET_DAMAGE,
    ENERGY_CONSERVED;

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
