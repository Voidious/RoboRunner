package robowiki.runner;

import robocode.control.RobotResults;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public class BattleListener extends BattleAdaptor {
  private Multimap<String, RobotResults> _botResults;

  public BattleListener() {
    _botResults = ArrayListMultimap.create();
  }

  public void onBattleCompleted(BattleCompletedEvent completedEvent) {
    RobotResults[] robotResultsArray =
        RobotResults.convertResults(completedEvent.getIndexedResults());
    for (RobotResults robotResults : robotResultsArray) {
      _botResults.put(robotResults.getTeamLeaderName(), robotResults);
    }
  }

  public void onBattleError(BattleErrorEvent battleErrorEvent) {
    System.out.println("Robocode error: " + battleErrorEvent.getError());
  }

  public Multimap<String, RobotResults> getRobotResultsMap() {
    return ImmutableMultimap.copyOf(_botResults);
  }

  public void clear() {
    _botResults.clear();
  }
}
