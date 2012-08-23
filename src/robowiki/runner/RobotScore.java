package robowiki.runner;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A robot's score data for a single battle or a set of battles.
 *
 * @author Voidious
 */
public class RobotScore {
  public static final Function<RobotScore, Double> NORMAL_SCORER =
      new Function<RobotScore, Double>() {
        @Override
        public Double apply(RobotScore robotScore) {
          return robotScore.score;
        }
      };
  public static final Function<RobotScore, Double> SURVIVAL_FIRSTS_SCORER = 
      new Function<RobotScore, Double>() {
        @Override
        public Double apply(RobotScore robotScore) {
          return robotScore.survivalRounds;
        }
      };
  public static final Function<RobotScore, Double> SURVIVAL_SCORER =
      new Function<RobotScore, Double>() {
        @Override
        public Double apply(RobotScore robotScore) {
          return robotScore.survivalScore;
        }
      };
  public static final Function<RobotScore, Double> BULLET_DAMAGE_SCORER =
      new Function<RobotScore, Double>() {
        @Override
        public Double apply(RobotScore robotScore) {
          return robotScore.bulletDamage;
        }
      };
  public static final Function<RobotScore, Double> MOVEMENT_CHALLENGE_SCORER =
      new Function<RobotScore, Double>() {
        @Override
        public Double apply(RobotScore robotScore) {
          return robotScore.energyConserved;
        }
      };

  public final String botName;
  public final double score;
  public final double survivalRounds;
  public final double survivalScore;
  public final double bulletDamage;
  public final double energyConserved;
  public final int numBattles;

  public RobotScore(String botName, double score, double survivalRounds,
      double survivalScore, double bulletDamage) {
    this(botName, score, survivalRounds, survivalScore, bulletDamage, 0, 1);
  }

  public RobotScore(String botName, double score, double survivalRounds,
      double survivalScore, double bulletDamage, double energyConserved) {
    this(botName, score, survivalRounds, survivalScore, bulletDamage,
        energyConserved, 1);
  }

  public RobotScore(String botName, double score, double survivalRounds,
      double survivalScore, double bulletDamage, double energyConserved,
      int numBattles) {
    this.botName = botName;
    this.score = score;
    this.survivalRounds = survivalRounds;
    this.survivalScore = survivalScore;
    this.bulletDamage = bulletDamage;
    this.energyConserved = energyConserved;
    this.numBattles = numBattles;
  }

  /**
   * Calculates this score relative to the given enemy score.
   * 
   * @param enemyScore score data for the enemy robot
   * @param numRounds number of rounds in the battle
   * @return the {@code RobotScore} relative to the given enemy robot score
   */
  public RobotScore getScoreRelativeTo(
      RobotScore enemyScore, int numRounds) {
    return getScoreRelativeTo(Lists.newArrayList(enemyScore), numRounds);
  }

  /**
   * Calculates this score relative to the given enemy scores.
   * 
   * @param enemyScore score data for the other robots in the battle
   * @param numRounds number of rounds in the battle
   * @return the {@code RobotScore} relative to the given enemy robot scores
   */
  public RobotScore getScoreRelativeTo(
      List<RobotScore> enemyScores, int numRounds) {
    return new RobotScore(botName,
        getAverageScore(RobotScore.NORMAL_SCORER, enemyScores),
        getAverageScore(RobotScore.SURVIVAL_FIRSTS_SCORER, enemyScores),
        getAverageScore(RobotScore.SURVIVAL_SCORER, enemyScores),
        bulletDamage / numRounds,
        getAverageEnergyConserved(enemyScores, numRounds),
        numBattles);
  }

  private double getAverageScore(
      Function<RobotScore, Double> scorer, Collection<RobotScore> enemyScores) {
    double totalScore = 0;
    double challengerScore = scorer.apply(this);
    int numScores = 0;
    for (RobotScore robotScore : enemyScores) {
      totalScore += 100 * (challengerScore
          / (challengerScore + scorer.apply(robotScore)));
      numScores++;
    }
    return totalScore / numScores;
  }

  private double getAverageEnergyConserved(
      List<RobotScore> enemyScores, int numRounds) {
    if (enemyScores.size() == 1) {
      return 100 - (enemyScores.get(0).bulletDamage / numRounds);
    }
    return 0;
  }

  /**
   * Calculates the average of two scores, weighted by number of battles.
   *
   * @param score1 the first score
   * @param score2 the second score
   * @return the combined score
   */
  public static RobotScore addScores(RobotScore score1, RobotScore score2) {
    Preconditions.checkNotNull(score1);
    Preconditions.checkNotNull(score2);
    Preconditions.checkArgument(score1.botName.equals(score2.botName));

    int addedBattles = score1.numBattles + score2.numBattles;
    double score = ((score1.score * score1.numBattles)
        + (score2.score * score2.numBattles))
            / addedBattles;
    double rounds = ((score1.survivalRounds * score1.numBattles)
        + (score2.survivalRounds * score2.numBattles))
            / addedBattles;
    double survival = ((score1.survivalScore * score1.numBattles)
        + (score2.survivalScore * score2.numBattles))
            / addedBattles;
    double damage = ((score1.bulletDamage * score1.numBattles)
        + (score2.bulletDamage * score2.numBattles))
            / addedBattles;
    double energy = ((score1.energyConserved * score1.numBattles)
        + (score2.energyConserved * score2.numBattles))
            / addedBattles;
    return new RobotScore(
        score1.botName, score, rounds, survival, damage, energy, addedBattles);
  }

  public enum ScoringStyle {
    PERCENT_SCORE("Average Percent Score", NORMAL_SCORER, false),
    SURVIVAL_FIRSTS("Survival Firsts", SURVIVAL_FIRSTS_SCORER, false),
    SURVIVAL_SCORE("Survival Score", SURVIVAL_SCORER, false),
    BULLET_DAMAGE("Bullet Damage", BULLET_DAMAGE_SCORER, true),
    MOVEMENT_CHALLENGE("Movement Challenge", MOVEMENT_CHALLENGE_SCORER, true);

    private String _description;
    private Function<RobotScore, Double> _scorer;
    private boolean _isChallenge;

    private ScoringStyle(String description,
        Function<RobotScore, Double> scorer, boolean isChallenge) {
      _description = description;
      _scorer = scorer;
      _isChallenge = isChallenge;
    }

    public static ScoringStyle parseStyle(String styleString) {
      if (styleString.contains("PERCENT_SCORE")) {
        return PERCENT_SCORE;
      } else if (styleString.contains("BULLET_DAMAGE")) {
        return BULLET_DAMAGE;
      } else if (styleString.contains("SURVIVAL_FIRSTS")) {
        return SURVIVAL_FIRSTS;
      } else if (styleString.contains("SURVIVAL_SCORE")) {
        return SURVIVAL_SCORE;
      } else if (styleString.contains("MOVEMENT_CHALLENGE")
                 || styleString.contains("ENERGY_CONSERVED")) {
        return MOVEMENT_CHALLENGE;
      } else {
        throw new IllegalArgumentException(
            "Unrecognized scoring style: " + styleString);
      }
    }

    public String getDescription() {
      return _description;
    }

    public boolean isChallenge() {
      return _isChallenge;
    }

    public double getScore(RobotScore score) {
      return _scorer.apply(score);
    }
  }
}
