package robowiki.runner;

import com.google.common.base.Function;

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

  public final double score;
  public final double survivalRounds;
  public final double survivalScore;
  public final double bulletDamage;
  public final double energyConserved;
  public final int numBattles;

  public RobotScore(double score, double survivalRounds, double survivalScore,
      double bulletDamage) {
    this(score, survivalRounds, survivalScore, bulletDamage, 0, 1);
  }

  public RobotScore(double score, double survivalRounds, double survivalScore,
      double bulletDamage, double energyConserved) {
    this(score, survivalRounds, survivalScore, bulletDamage, energyConserved,
        1);
  }

  public RobotScore(double score, double survivalRounds, double survivalScore,
      double bulletDamage, double energyConserved, int numBattles) {
    this.score = score;
    this.survivalRounds = survivalRounds;
    this.survivalScore = survivalScore;
    this.bulletDamage = bulletDamage;
    this.energyConserved = energyConserved;
    this.numBattles = numBattles;
  }

  public static RobotScore addScores(RobotScore score1, RobotScore newScore) {
    int addedBattles = score1.numBattles + newScore.numBattles;
    double score = ((score1.score * score1.numBattles)
        + (newScore.score * newScore.numBattles))
            / addedBattles;
    double rounds = ((score1.survivalRounds * score1.numBattles)
        + (newScore.survivalRounds * newScore.numBattles))
            / addedBattles;
    double survival = ((score1.survivalScore * score1.numBattles)
        + (newScore.survivalScore * newScore.numBattles))
            / addedBattles;
    double damage = ((score1.bulletDamage * score1.numBattles)
        + (newScore.bulletDamage * newScore.numBattles))
            / addedBattles;
    double energy = ((score1.energyConserved * score1.numBattles)
        + (newScore.energyConserved * newScore.numBattles))
            / addedBattles;

    return
        new RobotScore(score, rounds, survival, damage, energy, addedBattles);
  }

  public enum ScoringStyle {
    PERCENT_SCORE("Average Percent Score", NORMAL_SCORER),
    SURVIVAL_FIRSTS("Survival Firsts", SURVIVAL_FIRSTS_SCORER),
    SURVIVAL_SCORE("Survival Score", SURVIVAL_SCORER),
    BULLET_DAMAGE("Bullet Damage", BULLET_DAMAGE_SCORER),
    MOVEMENT_CHALLENGE("Movement Challenge", MOVEMENT_CHALLENGE_SCORER);

    private String _description;
    private Function<RobotScore, Double> _scorer;

    private ScoringStyle(
        String description, Function<RobotScore, Double> scorer) {
      _description = description;
      _scorer = scorer;
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

    public double getScore(RobotScore score) {
      return _scorer.apply(score);
    }
  }
}
