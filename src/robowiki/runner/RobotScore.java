package robowiki.runner;

public class RobotScore {
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
}
