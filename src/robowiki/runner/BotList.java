package robowiki.runner;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BotList {
  private List<String> _botNames;

  public BotList(String botName) {
    _botNames = Lists.newArrayList(botName);
  }

  public BotList(List<String> botNames) {
    _botNames = Lists.newArrayList(botNames);
  }

  public List<String> getBotNames() {
    return ImmutableList.copyOf(_botNames);
  }
}
