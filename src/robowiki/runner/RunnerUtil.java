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

  public static String parseArgument(
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
}
