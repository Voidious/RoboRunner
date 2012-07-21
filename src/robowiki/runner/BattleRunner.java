package robowiki.runner;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

public class BattleRunner {
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private Queue<RobocodeEngine> _engineQueue;
  private static ExecutorService _threadPool;
  private Map<RobocodeEngine, BattleListener> _listeners;
  private BattlefieldSpecification _battlefield;
  private int _numRounds;

  public BattleRunner(Set<String> robocodeEnginePaths, int numRounds,
      int battleFieldWidth, int battleFieldHeight) {
    _numRounds = numRounds;
    _battlefield =
        new BattlefieldSpecification(battleFieldWidth, battleFieldHeight);

    _threadPool = Executors.newFixedThreadPool(robocodeEnginePaths.size());
    _engineQueue = Queues.newConcurrentLinkedQueue();
    _listeners = Maps.newHashMap();
    for (String enginePath : robocodeEnginePaths) {
      initEngine(enginePath);
    }
  }

  private void initEngine(String enginePath) {
    RobocodeEngine engine = new RobocodeEngine(new File(enginePath));
    BattleListener listener = new BattleListener();
    engine.addBattleListener(listener);
    engine.setVisible(false);
    _engineQueue.add(engine);
    _listeners.put(engine, listener);
  }

  public void runBattles(List<BotSet> battlesToRun) {
    for (final BotSet botSet : battlesToRun) {
      Future<?> future = _threadPool.submit(newBattleRunnable(botSet));
      try {
        future.get();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
  }

  private Runnable newBattleRunnable(final BotSet botSet) {
    return new Runnable() {
      @Override
      public void run() {
        RobocodeEngine engine = _engineQueue.poll();
        BattleListener listener = _listeners.get(engine);
        BattleSpecification battleSpec = new BattleSpecification(
            _numRounds, _battlefield, 
        engine.getLocalRepository(COMMA_JOINER.join(botSet.getBotNames())));
        engine.runBattle(battleSpec);
        engine.waitTillBattleOver();
        _engineQueue.add(engine);
        System.out.println("Battle result: ");
        System.out.print(listener.getLastBattleResultString());
      }
    };
  }

  public void shutdown() {
    _threadPool.shutdown();
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
}
