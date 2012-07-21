package robowiki.runner;

import java.util.Map;

import com.google.common.collect.Maps;

import robocode.control.RobotResults;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

public class BattleListener extends BattleAdaptor {
  private Map<String, RobotResults> _botResults;
  private RobotResults[] _lastBattleResults;

  public BattleListener() {
    _botResults = Maps.newHashMap();
  }

  public void onBattleCompleted(BattleCompletedEvent completedEvent) {
    _botResults.clear();
    _lastBattleResults =
        RobotResults.convertResults(completedEvent.getIndexedResults());
    for (RobotResults robotResults : _lastBattleResults) {
      _botResults.put(
          robotResults.getRobot().getNameAndVersion(), robotResults);
    }
  }

  public void onBattleError(BattleErrorEvent battleErrorEvent) {
    System.out.println("Robocode error: " + battleErrorEvent.getError());
  }

  public RobotResults getRobotResults(String botName) {
    return _botResults.get(botName);
  }

  public String getLastBattleResultString() {
    StringBuilder resultString = new StringBuilder();
    for (RobotResults robotResults : _lastBattleResults) {
      resultString.append("  ");
      resultString.append(robotResults.getRobot().getNameAndVersion());
      resultString.append(": ");
      resultString.append(robotResults.getScore());
      resultString.append("\n");
    }
    return resultString.toString();
  }
}
