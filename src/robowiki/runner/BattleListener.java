package robowiki.runner;

import java.util.Map;

import com.google.common.collect.Maps;

import robocode.control.RobotResults;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

public class BattleListener extends BattleAdaptor {
  private Map<String, RobotResults> _botResults;

  public BattleListener() {
    _botResults = Maps.newHashMap();
  }

  public void onBattleCompleted(BattleCompletedEvent completedEvent) {
    System.out.println("Battle completed ok");
    RobotResults[] robotResultsArray =
        RobotResults.convertResults(completedEvent.getIndexedResults());
    if (robotResultsArray.length > 2) {
      System.out.println("Results array > 2");
    }
    for (RobotResults robotResults : robotResultsArray) {
      System.out.println("  " + robotResults.getRobot().getNameAndVersion());
      _botResults.put(
          robotResults.getRobot().getNameAndVersion(), robotResults);
    }
  }

  public void onBattleError(BattleErrorEvent battleErrorEvent) {
    System.out.println("Robocode error: " + battleErrorEvent.getError());
  }

  public Map<String, RobotResults> getRobotResultsMap() {
    if (_botResults.size() > 2) {
      System.out.println("How is results map > 2");
    }
    return Maps.newHashMap(_botResults);
  }

  public void clear() {
    _botResults.clear();
  }
}
