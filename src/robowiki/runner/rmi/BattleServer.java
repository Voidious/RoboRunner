package robowiki.runner.rmi;

import java.io.File;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotResults;
import robowiki.runner.BattleListener;
import robowiki.runner.rmi.BattleServer.BotSet;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class BattleServer implements RemoteBattleServer {
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private RobocodeEngine _engine;
  private BattleListener _listener;
  private BattlefieldSpecification _battlefield;
  private int _numRounds;

  public static void main(String[] args) {
//    if (System.getSecurityManager() == null) {
//      System.setSecurityManager(new SecurityManager());
//    }
    try {
        String name = "rr1";
        RemoteBattleServer battleServer = new BattleServer(
            "/Users/pcupka/roborunner/robocodes/r1", 5, 800, 600);
        RemoteBattleServer stub = (RemoteBattleServer)
            UnicastRemoteObject.exportObject(battleServer, 0);
        Registry registry = LocateRegistry.getRegistry();
        registry.rebind(name, stub);
        System.out.println("BattleServer bound");
    } catch (Exception e) {
        System.err.println("BattleServer exception:");
        e.printStackTrace();
    }
  }

  public BattleServer(String robocodeEnginePath, int numRounds,
      int battleFieldWidth, int battleFieldHeight) {
    _numRounds = numRounds;
    _battlefield =
        new BattlefieldSpecification(battleFieldWidth, battleFieldHeight);

    _listener = new BattleListener();
    _engine = new RobocodeEngine(new File(robocodeEnginePath));
    _engine.addBattleListener(_listener);
    _engine.setVisible(false);
  }

  @Override
  public Map<String, RobotResults> runBattle(BotSet botSet) {
    BattleSpecification battleSpec = new BattleSpecification(
        _numRounds, _battlefield, 
    _engine.getLocalRepository(COMMA_JOINER.join(botSet.getBotNames())));
    _engine.runBattle(battleSpec, true);
    Map<String, RobotResults> resultsMap = _listener.getRobotResultsMap();
    _listener.clear();
    return resultsMap;
  }

  public static class BotSet implements Serializable {
    private static final long serialVersionUID = 3609233932802133555L;
    private ArrayList<String> _botNames;

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

interface RemoteBattleServer extends Remote {
  Map<String, RobotResults> runBattle(BotSet botSet)
      throws RemoteException;
}

