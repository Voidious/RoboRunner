package robowiki.runner;

public class ScoreError {
  public final double standardDeviation;
  public final int numBattles;
  public final double avgTime;

  public ScoreError(
      double standardDeviation, int numBattles, double avgTime) {
    this.standardDeviation = standardDeviation;
    this.numBattles = numBattles;
    this.avgTime = avgTime;
  }

  public double getStandardError() {
    return getStandardError(numBattles);
  }

  private double getStandardError(int errorBattles) {
    return standardDeviation / Math.sqrt(errorBattles);
  }

  public double getAccuracyGainRate() {
    return (getStandardError(numBattles) - getStandardError(numBattles + 1))
        / avgTime;
  }
}
