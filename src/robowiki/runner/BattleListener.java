package robowiki.runner;

import java.util.Map;

import robocode.control.RobotResults;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

import com.google.common.collect.Maps;

public class BattleListener extends BattleAdaptor {
  private Map<String, RobotResults> _botResults;

  public BattleListener() {
    _botResults = Maps.newHashMap();
  }

  public void onBattleCompleted(BattleCompletedEvent completedEvent) {
    RobotResults[] robotResultsArray =
        RobotResults.convertResults(completedEvent.getIndexedResults());
    for (RobotResults robotResults : robotResultsArray) {
      _botResults.put(
          robotResults.getRobot().getNameAndVersion(), robotResults);
    }
  }

  public void onBattleError(BattleErrorEvent battleErrorEvent) {
    System.out.println("Robocode error: " + battleErrorEvent.getError());
  }

  public Map<String, RobotResults> getRobotResultsMap() {
    return Maps.newHashMap(_botResults);
  }

  public void clear() {
    _botResults.clear();
  }
}
