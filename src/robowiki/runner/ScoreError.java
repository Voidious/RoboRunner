package robowiki.runner;

import java.util.List;
import java.util.Random;

public class ScoreError {
  private static final Random RANDOM = new Random();

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

  public double generateRandomAverageScore() {
    double scoreTotal = 0;
    for (int x = 0; x < numBattles; x++) {
      scoreTotal += Math.max(0, Math.min(100, average
          + (RANDOM.nextGaussian() * standardDeviation)));
    }
    return scoreTotal / numBattles;
  }
}
