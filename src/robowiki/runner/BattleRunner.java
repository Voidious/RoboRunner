package robowiki.runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

public class BattleRunner {
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private Queue<Process> _processQueue;
  private ExecutorService _threadPool;
  private ExecutorService _callbackPool;
  private int _numRounds;
  private int _battleFieldWidth;
  private int _battleFieldHeight;

  public BattleRunner(Set<String> robocodeEnginePaths, String jvmArgs,
      int numRounds, int battleFieldWidth, int battleFieldHeight) {
    _numRounds = numRounds;
    _battleFieldWidth = battleFieldWidth;
    _battleFieldHeight = battleFieldHeight;

    _threadPool = Executors.newFixedThreadPool(robocodeEnginePaths.size());
    _callbackPool = Executors.newFixedThreadPool(1);
    _processQueue = Queues.newConcurrentLinkedQueue();
    for (String enginePath : robocodeEnginePaths) {
      initEngine(enginePath, jvmArgs);
    }
  }

  private void initEngine(String enginePath, String jvmArgs) {
    try {
      List<String> command = Lists.newArrayList();
      command.add("java");
      command.addAll(Lists.newArrayList(jvmArgs.trim().split(" +")));
      command.addAll(Lists.newArrayList("-cp",
          System.getProperty("java.class.path"),
          "robowiki.runner.BattleProcess", "-rounds", "" + _numRounds,
          "-width", "" + _battleFieldWidth, "-height", "" + _battleFieldHeight,
          "-path", enginePath));

      System.out.print("Initializing engine: " + enginePath + "... ");
      ProcessBuilder builder = new ProcessBuilder(command);
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

  public void runBattles(List<BotList> botLists, BattleResultHandler handler) {
    List<Future<String>> futures = Lists.newArrayList();
    for (final BotList botList : botLists) {
      futures.add(_threadPool.submit(newBattleCallable(botList, handler)));
    }
    getAllFutures(futures);
  }

  public void runBattles(
      BattleSelector selector, BattleResultHandler handler, int numBattles) {
    List<Future<String>> futures = Lists.newArrayList();
    for (int x = 0; x < numBattles; x++) {
      futures.add(_threadPool.submit(newBattleCallable(selector, handler)));
    }
    getAllFutures(futures);
  }

  private void getAllFutures(List<Future<String>> futures) {
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
      BotList botList, BattleResultHandler handler) {
    return new BattleCallable(botList, handler);
  }

  private Callable<String> newBattleCallable(
      BattleSelector selector, BattleResultHandler handler) {
    return new BattleCallable(selector, handler);
  }

  private List<RobotScore> getRobotScoreList(String battleResults) {
    List<RobotScore> robotScores = Lists.newArrayList();
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
          new RobotScore(botName, score, firsts, survivalScore, bulletDamage);
      robotScores.add(robotScore);
    }
    return ImmutableList.copyOf(robotScores);
  }

  private boolean isBattleResult(String line) {
    return line != null && line.startsWith(BattleProcess.RESULT_SIGNAL);
  }

  public void shutdown() {
    _threadPool.shutdown();
    _callbackPool.shutdown();
  }

  public interface BattleResultHandler {
    /**
     * Processes the scores from a battle.
     *
     * @param robotScores scores for each robot in the battle
     * @param elapsedTime elapsed time of the battle, in nanoseconds
     */
    void processResults(List<RobotScore> robotScores, long elapsedTime);
  }

  public interface BattleSelector {
    BotList nextBotList();
  }

  private class BattleCallable implements Callable<String> {
    private BotList _botList;
    private BattleSelector _selector;
    private BattleResultHandler _listener;

    public BattleCallable(BotList botList, BattleResultHandler listener) {
      _botList = botList;
      _listener = listener;
    }

    public BattleCallable(
        BattleSelector selector, BattleResultHandler listener) {
      _selector = selector;
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
      BotList botList;
      if (_selector == null) {
        botList = _botList;
      } else {
        botList = _callbackPool.submit(new Callable<BotList>() {
          @Override
          public BotList call() throws Exception {
            return _selector.nextBotList();
          }
        }).get();
      }
      writer.append(COMMA_JOINER.join(botList.getBotNames()) + "\n");
      writer.flush();
      String input;
      do {
        // TODO: How to handle other output, errors etc?
        input = reader.readLine();
      } while (!isBattleResult(input));
      final String result = input;
      _processQueue.add(battleProcess);
      _callbackPool.submit(new Runnable() {
        @Override
        public void run() {
          _listener.processResults(
              getRobotScoreList(result), System.nanoTime() - startTime);
        }
      }).get();
      return result;
    }
  }
}
