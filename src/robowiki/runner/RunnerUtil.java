package robowiki.runner;

import java.util.List;

import com.google.common.collect.Lists;

public class RunnerUtil {
  public static String[] getCombinedArgs(String[] args) {
    List<String> argsList = Lists.newArrayList();
    String nextArg = "";
    for (String arg : args) {
      if (arg.startsWith("-")) {
        if (!nextArg.equals("")) {
          argsList.add(nextArg);
          nextArg = "";
        }
        argsList.add(arg);
      } else {
        nextArg = (nextArg + " " + arg).trim();
      }
    }
    if (!nextArg.equals("")) {
      argsList.add(nextArg);
    }
    return argsList.toArray(new String[0]);
  }

  public static String parseStringArgument(String flagName, String[] args) {
    return parseStringArgument(flagName, args, null);
  }

  public static String parseStringArgument(
      String flagName, String[] args, String missingError) {
    for (int x = 0; x < args.length - 1; x++) {
      if (args[x].equals("-" + flagName)) {
        return args[x+1];
      }
    }
    if (missingError != null) {
      System.out.println(missingError);
    }
    return null;
  }

  public static boolean parseBooleanArgument(String flagName, String[] args) {
    for (int x = 0; x < args.length; x++) {
      if (args[x].equals("-" + flagName)) {
        return true;
      }
    }
    return false;
  }

  public static double round(double d, int i) {
    long powerTen = 1;
    for (int x = 0; x < i; x++) {
      powerTen *= 10;
    }
    return ((double) Math.round(d * powerTen)) / powerTen;
  }

  public static double standardDeviation(List<Double> values) {
    double avg = average(values);
    double sumSquares = 0;
    for (double value : values) {
      sumSquares += square(avg - value);
    }
    return Math.sqrt(sumSquares / values.size());
  }

  public static double square(double d) {
    return d * d;
  }

  public static double average(List<Double> values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return (sum / values.size());
  }
}
