package robowiki.runner.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;

import robocode.control.RobotResults;
import robowiki.runner.rmi.BattleServer.BotSet;

import com.google.common.collect.Lists;

public class RemoteBattleRunner {
  public static void main(String[] args) {
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new SecurityManager());
    }
    try {
        String name = "rr1";
        Registry registry = LocateRegistry.getRegistry(args[0]);
        RemoteBattleServer battleServer =
            (RemoteBattleServer) registry.lookup(name);
        Map<String, RobotResults> resultsMap = battleServer.runBattle(
            new BotSet(Lists.newArrayList(
                "voidious.Diamond 1.7.56", "jam.mini.Raiko 0.43")));
        for (String bot : resultsMap.keySet()) {
          System.out.println(bot + ": " + resultsMap.get(bot).getScore());
        }
    } catch (Exception e) {
        System.err.println("ComputePi exception:");
        e.printStackTrace();
    }
  }
}
