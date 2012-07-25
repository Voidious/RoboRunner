package robowiki.runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

public class BattleRunner {
  private static final String ROBORUNNER_JAR = "roborunner-0.4.0.jar";
  private static final Joiner COMMA_JOINER = Joiner.on(",");
  private static final String SLASH = System.getProperty("file.separator");
  private static final String COLON = System.getProperty("path.separator");

  private Queue<Process> _processQueue;
  private ExecutorService _threadPool;
  private ExecutorService _resultPool;
  private int _numRounds;
  private int _battleFieldWidth;
  private int _battleFieldHeight;

  public BattleRunner(Set<String> robocodeEnginePaths, int numRounds,
      int battleFieldWidth, int battleFieldHeight) {
    _numRounds = numRounds;
    _battleFieldWidth = battleFieldWidth;
    _battleFieldHeight = battleFieldHeight;

    _threadPool = Executors.newFixedThreadPool(robocodeEnginePaths.size());
    _resultPool = Executors.newFixedThreadPool(1);
    _processQueue = Queues.newConcurrentLinkedQueue();
    for (String enginePath : robocodeEnginePaths) {
      initEngine(enginePath);
    }
  }

  private void initEngine(String enginePath) {
    try {
      System.out.print("Initializing engine: " + enginePath + "... ");
      ProcessBuilder builder = new ProcessBuilder("java", "-Xmx512M", "-cp",
          enginePath + SLASH + "libs" + SLASH + "robocode.jar" + COLON + "."
          + SLASH + "lib" + SLASH + "guava-12.0.1.jar" + COLON + "." + SLASH
          + "lib" + SLASH + ROBORUNNER_JAR,
          "robowiki.runner.BattleProcess", "-rounds", "" + _numRounds,
          "-width", "" + _battleFieldWidth, "-height", "" + _battleFieldHeight,
          "-path", enginePath);
      builder.redirectErrorStream(true);
      Process battleProcess = builder.start();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(battleProcess.getInputStream()));
      String processOutput;
      do {
        processOutput = reader.readLine();
      } while (!processOutput.equals(BattleProcess.READY_SIGNAL));
      System.out.println("done!");
      _processQueue.add(battleProcess);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void runBattles(List<BotSet> botSets, BattleResultHandler handler) {
    List<Future<String>> futures = Lists.newArrayList();
    for (final BotSet botSet : botSets) {
      futures.add(_threadPool.submit(newBattleCallable(botSet, handler)));
    }

    for (Future<String> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
  }

  private Callable<String> newBattleCallable(
      final BotSet botSet, final BattleResultHandler handler) {
    return new BattleCallable(botSet, handler);
  }

  private Map<String, RobotScore> getRobotScoreMap(String battleResults) {
    Map<String, RobotScore> scoreMap = Maps.newHashMap();
    String[] botScores =
        battleResults.replaceFirst(BattleProcess.RESULT_SIGNAL, "")
        .replaceAll("\n", "").split(BattleProcess.BOT_DELIMITER);
    for (String scoreString : botScores) {
      String[] scoreFields = scoreString.split(BattleProcess.SCORE_DELIMITER);
      String botName = scoreFields[0];
      int score = Integer.parseInt(scoreFields[1]);
      int firsts = Integer.parseInt(scoreFields[2]);
      int survivalScore = Integer.parseInt(scoreFields[3]);
      double bulletDamage = Double.parseDouble(scoreFields[4]);
      RobotScore robotScore =
          new RobotScore(score, firsts, survivalScore, bulletDamage);
      scoreMap.put(botName, robotScore);
    }
    return scoreMap;
  }

  private boolean isBattleResult(String line) {
    return line.startsWith(BattleProcess.RESULT_SIGNAL);
  }

  public void shutdown() {
    _threadPool.shutdown();
    _resultPool.shutdown();
  }

  public static class BotSet {
    private List<String> _botNames;

    public BotSet(String botName) {
      _botNames = Lists.newArrayList(botName);
    }

    public BotSet(List<String> botNames) {
      _botNames = Lists.newArrayList(botNames);
    }

    public List<String> getBotNames() {
      return _botNames;
    }
  }

  public static class RobotScore {
    public final int score;
    public final int survivalRounds;
    public final double survivalScore;
    public final double bulletDamage;

    public RobotScore(int score, int survivalRounds, double survivalScore,
        double bulletDamage) {
      this.score = score;
      this.survivalRounds = survivalRounds;
      this.survivalScore = survivalScore;
      this.bulletDamage = bulletDamage;
    }
  }

  public interface BattleResultHandler {
    void processResults(Map<String, RobotScore> robotScoreMap, long nanoTime);
  }

  private class BattleCallable implements Callable<String> {
    private BotSet _botSet;
    private BattleResultHandler _listener;

    public BattleCallable(BotSet botSet, BattleResultHandler listener) {
      _botSet = botSet;
      _listener = listener;
    }

    @Override
    public String call() throws Exception {
      final long startTime = System.nanoTime();
      Process battleProcess = _processQueue.poll();
      BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(battleProcess.getOutputStream()));
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(battleProcess.getInputStream()));
      writer.append(COMMA_JOINER.join(_botSet.getBotNames()) + "\n");
      writer.flush();
      String input;
      do {
        // TODO: How to handle other output, errors etc?
        input = reader.readLine();
      } while (!isBattleResult(input));
      final String result = input;
      _processQueue.add(battleProcess);
      _resultPool.submit(new Runnable() {
        @Override
        public void run() {
          _listener.processResults(
              getRobotScoreMap(result), System.nanoTime() - startTime);
        }
      }).get();
      return result;
    }
  }
}
