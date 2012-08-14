package robowiki.runner;

import java.util.List;

public class ScoreError {
  public final double average;
  public final double standardDeviation;
  public final int numBattles;
  public final double avgTime;

  public ScoreError(List<Double> scores, double avgTime) {
    this.average = RunnerUtil.average(scores);
    this.standardDeviation = RunnerUtil.standardDeviation(scores);
    this.numBattles = scores.size();
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
