package robowiki.runner;

import java.util.List;

public class ScoreError {
  public final double sampleError;
  public final int numBattles;
  public final double avgTime;

  public ScoreError(List<Double> scores, double avgTime) {
    this.sampleError = standardDeviation(scores);
    this.numBattles = scores.size();
    this.avgTime = avgTime;
  }

  public double getConfidence() {
    return 1.96 * getStandardError(numBattles);
  }

  private double getStandardError(int errorBattles) {
    return sampleError / Math.sqrt(errorBattles);
  }

  public double getAccuracyGainRate() {
    return (getStandardError(numBattles) - getStandardError(numBattles + 1))
        / avgTime;
  }

  private double standardDeviation(List<Double> values) {
    double avg = average(values);
    double sumSquares = 0;
    for (double value : values) {
      sumSquares += square(avg - value);
    }
    return Math.sqrt(sumSquares / values.size());
  }

  private double square(double d) {
    return d * d;
  }

  private double average(List<Double> values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return (sum / values.size());
  }
}
