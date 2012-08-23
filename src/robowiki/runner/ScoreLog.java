package robowiki.runner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Score history for a challenger bot. Saves to and loads from XML files.
 *
 * @author Voidious
 */
public class ScoreLog {
  private static final String CHALLENGER = "challenger";
  private static final String SCORES = "scores";
  private static final String BOT_LIST = "bot_list";
  private static final String BOTS = "bots";
  private static final String BATTLE = "battle";
  private static final String ROBOT_SCORE = "robot_score";
  private static final String NAME = "name";
  private static final String SCORE = "score";
  private static final String SURVIVAL_ROUNDS = "rounds";
  private static final String SURVIVAL_SCORE = "survival";
  private static final String DAMAGE = "damage";
  private static final String NUM_ROUNDS = "num_rounds";
  private static final String TIME = "time";
  private static final Joiner COMMA_JOINER = Joiner.on(",");
  private static final Function<RobotScore, String> ROBOT_SCORE_NAME_TRANSFORMER
      = new Function<RobotScore, String>() {
        @Override
        public String apply(RobotScore robotScore) {
          return robotScore.botName;
        }
      };

  private static XMLEventFactory XML_EVENT_FACTORY =
      XMLEventFactory.newInstance();
  private static XMLEvent XML_TAB = XML_EVENT_FACTORY.createDTD("\t");
  private static XMLEvent XML_NL = XML_EVENT_FACTORY.createDTD("\n");

  public final String challenger;
  private Map<String, List<BattleScore>> _scores;
  private List<String> _botLists;

  public ScoreLog(String challenger) {
    this.challenger = Preconditions.checkNotNull(challenger);
    _scores = Maps.newHashMap();
    _botLists = Lists.newArrayList();
  }

  /**
   * Adds the results of a Robocode battle to the data store.
   *
   * @param robotScores scores for each robot in the battle
   * @param numRounds number of rounds in the battle
   * @param elapsedTime elapsed time of the battle, in nanoseconds
   */
  public void addBattle(
      List<RobotScore> robotScores, int numRounds, long elapsedTime) {
    String botListString = getSortedBotListFromScores(robotScores);
    if (!_scores.containsKey(botListString)) {
      _scores.put(botListString, Lists.<BattleScore>newArrayList());
      _botLists.add(botListString);
    }
    _scores.get(botListString).add(
        new BattleScore(robotScores, numRounds, elapsedTime));
  }

  public String getSortedBotListFromScores(List<RobotScore> robotScores) {
    List<String> botList = Lists.newArrayList(Lists.transform(
        robotScores, ROBOT_SCORE_NAME_TRANSFORMER));
    botList.remove(challenger);
    return getSortedBotList(botList);
  }

  public String getSortedBotList(List<String> botList) {
    List<String> sortedBotList = Lists.newArrayList(botList);
    Collections.sort(sortedBotList);
    return COMMA_JOINER.join(sortedBotList);
  }

  public List<String> getBotLists() {
    return ImmutableList.copyOf(_botLists);
  }

  public boolean hasBotList(String botListString) {
    return _scores.containsKey(botListString);
  }

  public List<BattleScore> getBattleScores(String botList) {
    return ImmutableList.copyOf(_scores.get(botList));
  }

  public BattleScore getLastBattleScore(String botList) {
    if (!_scores.containsKey(botList)) {
      return null;
    }
    List<BattleScore> battleScores = _scores.get(botList);
    return battleScores.get(battleScores.size() - 1);
  }

  public BattleScore getAverageBattleScore(String botList) {
    if (!_scores.containsKey(botList)) {
      return null;
    }

    List<BattleScore> battleScores = _scores.get(botList);
    Multimap<String, RobotScore> totalScores = LinkedListMultimap.create();
    int totalRounds = 0;
    long totalTime = 0;
    boolean initializeTotalScores = true;
    for (BattleScore battleScore : battleScores) {
      for (RobotScore robotScore : battleScore.getRobotScores()) {
        if (initializeTotalScores) {
          totalScores.put(robotScore.botName, robotScore);
        } else {
          LinkedList<RobotScore> scores =
              Lists.newLinkedList(totalScores.get(robotScore.botName));
          RobotScore oldScore = scores.removeFirst();
          scores.add(RobotScore.addScores(oldScore, robotScore));
          totalScores.replaceValues(robotScore.botName, scores);
        }
      }
      initializeTotalScores = false;
      totalRounds += battleScore.getNumRounds();
      totalTime += battleScore.getElapsedTime();
    }
    return new BattleScore(totalScores.values(),
        totalRounds / battleScores.size(), totalTime / battleScores.size());
  }

  public int getBattleCount(List<BotList> allReferenceBots) {
    int battles = 0;
    for (BotList botList : allReferenceBots) {
      String botListString = getSortedBotList(botList.getBotNames());
      if (_scores.containsKey(botListString)) {
        battles += _scores.get(botListString).size();
      }
    }
    return battles;
  }

  /**
   * Reads in the scores from an XML data file and creates a new
   * {@code ScoreLog} with the battle data.
   *
   * @param inputFilePath path of the XML data file
   * @return a new {@code ScoreLog} with the scores from the input file
   * @throws XMLStreamException if the XML file is not in the expected format
   * @throws FileNotFoundException if the file doesn't exist
   * @throws IOException
   */
  public static ScoreLog loadScoreLog(String inputFilePath)
      throws XMLStreamException, FileNotFoundException, IOException {
    ScoreLog scoreLog = null;
    List<RobotScore> robotScores = null;
    int numRounds = 0;
    long time = 0;

    XMLEventReader eventReader =
        XMLInputFactory.newInstance().createXMLEventReader(
            new GZIPInputStream(new FileInputStream(inputFilePath)));
    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();
      if (event.isStartElement()) {
        String localPart = event.asStartElement().getName().getLocalPart();
        if (localPart.equals(SCORES)) {
          scoreLog = new ScoreLog(getAttribute(event, CHALLENGER));
        } else if (localPart.equals(BATTLE)) {
          robotScores = Lists.newArrayList();
        } else if (localPart.equals(ROBOT_SCORE)) {
          robotScores.add(readRobotScore(eventReader));
        } else if (localPart.equals(NUM_ROUNDS)) {
          event = eventReader.nextEvent();
          numRounds = Integer.parseInt(event.asCharacters().getData());
        } else if (localPart.equals(TIME)) {
          event = eventReader.nextEvent();
          time = Long.parseLong(event.asCharacters().getData());
        }
      } else if (event.isEndElement()) {
        String localPart = event.asEndElement().getName().getLocalPart();
        if (localPart.equals(BATTLE)) {
          scoreLog.addBattle(robotScores, numRounds, time);
        }
      }
    }
    return scoreLog;
  }

  @SuppressWarnings("unchecked")
  private static String getAttribute(XMLEvent event, String challenger2) {
    Iterator<Attribute> attributes = event.asStartElement().getAttributes();
    while (attributes.hasNext()) {
      Attribute attribute = attributes.next();
      if (attribute.getName().toString().equals(CHALLENGER)) {
        return attribute.getValue();
      }
    }
    return null;
  }

  private static RobotScore readRobotScore(XMLEventReader eventReader)
      throws XMLStreamException {
    String name = null;
    double score = 0;
    double rounds = 0;
    double survival = 0;
    double damage = 0;
    while (true) {
      XMLEvent event = eventReader.nextEvent();
      if (event.isEndElement()
          && event.asEndElement().getName().getLocalPart().equals(
              ROBOT_SCORE)) {
        return new RobotScore(name, score, rounds, survival, damage);
      } else {
        if (event.isStartElement()) {
          String localPart = event.asStartElement().getName().getLocalPart();
          event = eventReader.nextEvent();
          if (localPart.equals(NAME)) {
            name = event.asCharacters().getData();
          } else if (localPart.equals(SCORE)) {
            score = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(SURVIVAL_ROUNDS)) {
            rounds = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(SURVIVAL_SCORE)) {
            survival = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(DAMAGE)) {
            damage = Double.parseDouble(event.asCharacters().getData());
          }
        }
      }
    }
  }

  /**
   * Save the scores to an output file in XML format.
   *
   * @param outputFilePath the path of the output file
   */
  public void saveScoreLog(String outputFilePath) {
    XMLEventWriter eventWriter = null;
    GZIPOutputStream gzipOutputStream = null;
    try {
      gzipOutputStream =
          new GZIPOutputStream(new FileOutputStream(outputFilePath));
      eventWriter =
          XMLOutputFactory.newInstance().createXMLEventWriter(gzipOutputStream);
      eventWriter.add(XML_EVENT_FACTORY.createStartDocument());
      eventWriter.add(XML_NL);
      writeStartElement(
          eventWriter, SCORES, createAttributes(CHALLENGER, challenger), 0);

      List<String> botListStrings = Lists.newArrayList(_scores.keySet());
      Collections.sort(botListStrings);
      for (String botList : botListStrings) {
        writeStartElement(
            eventWriter, BOT_LIST, createAttributes(BOTS, botList), 1);

        for (BattleScore battleScore : _scores.get(botList)) {
          writeStartElement(eventWriter, BATTLE, 2);
          for (RobotScore robotScore : battleScore.getRobotScores()) {
            writeStartElement(eventWriter, ROBOT_SCORE, 3);
            writeValue(eventWriter, NAME, robotScore.botName, 4);
            writeValue(eventWriter, SCORE, Math.round(robotScore.score), 4);
            writeValue(eventWriter, SURVIVAL_ROUNDS,
                Math.round(robotScore.survivalRounds), 4);
            writeValue(eventWriter, SURVIVAL_SCORE,
                Math.round(robotScore.survivalScore), 4);
            writeValue(eventWriter, DAMAGE,
                Math.round(robotScore.bulletDamage), 4);
            writeEndElement(eventWriter, ROBOT_SCORE, 3);
          }
          writeValue(eventWriter, NUM_ROUNDS,
              Integer.toString(battleScore.getNumRounds()), 3);
          writeValue(eventWriter, TIME,
              Long.toString(battleScore.getElapsedTime()), 3);
          writeEndElement(eventWriter, BATTLE, 2);
        }
        writeEndElement(eventWriter, botList, 1);
      }

      writeEndElement(eventWriter, SCORES, 0);
      eventWriter.add(XML_EVENT_FACTORY.createEndDocument());
      eventWriter.close();
      gzipOutputStream.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (XMLStreamException e) {
      e.printStackTrace();
    } finally {
      if (eventWriter != null) {
        try {
          eventWriter.close();
        } catch (XMLStreamException e) {
          e.printStackTrace();
        }
      }
      if (gzipOutputStream != null) {
        try {
          gzipOutputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private List<Attribute> createAttributes(String name, String value) {
    return Lists.newArrayList(
        XML_EVENT_FACTORY.createAttribute(new QName(name), value));
  }

  private void writeStartElement(XMLEventWriter eventWriter, String name,
      int numTabs) throws XMLStreamException {
    writeElement(eventWriter, name, numTabs, null, true);
  }

  private void writeStartElement(XMLEventWriter eventWriter, String name,
      List<Attribute> attributes, int numTabs) throws XMLStreamException {
    writeElement(eventWriter, name, numTabs, attributes, true);
  }

  private void writeEndElement(XMLEventWriter eventWriter, String name,
      int numTabs) throws XMLStreamException {
    writeElement(eventWriter, name, numTabs, null, false);
  }

  private void writeElement(XMLEventWriter eventWriter, String name,
      int numTabs, List<Attribute> attributes, boolean start)
      throws XMLStreamException {
    for (int x = 0; x < numTabs; x++) {
      eventWriter.add(XML_TAB);
    }
    if (start) {
      eventWriter.add(XML_EVENT_FACTORY.createStartElement(new QName(name),
          (attributes == null ? null : attributes.iterator()), null));
    } else {
      eventWriter.add(XML_EVENT_FACTORY.createEndElement("", "", name));
    }
    eventWriter.add(XML_NL);
  }

  private void writeValue(XMLEventWriter eventWriter, String name, long value,
      int numTabs) throws XMLStreamException {
    writeValue(eventWriter, name, Long.toString(value), numTabs);
  }

  private void writeValue(XMLEventWriter eventWriter, String name, String value,
      int numTabs) throws XMLStreamException {
    for (int x = 0; x < numTabs; x++) {
      eventWriter.add(XML_TAB);
    }
    eventWriter.add(XML_EVENT_FACTORY.createStartElement("", "", name));
    eventWriter.add(XML_EVENT_FACTORY.createCharacters(value));
    eventWriter.add(XML_EVENT_FACTORY.createEndElement("", "", name));
    eventWriter.add(XML_NL);
  }

  /**
   * Scores for each robot in a single battle.
   *
   * @author Voidious
   */
  public static class BattleScore {
    private final List<RobotScore> _robotScores;
    private final int _numRounds;
    private final long _elapsedTime;

    public BattleScore(
        Collection<RobotScore> scores, int numRounds, long nanoTime) {
      _robotScores = ImmutableList.copyOf(scores);
      _numRounds = numRounds;
      _elapsedTime = nanoTime;
    }

    public List<RobotScore> getRobotScores() {
      return _robotScores;
    }

    public int getNumRounds() {
      return _numRounds;
    }

    public long getElapsedTime() {
      return _elapsedTime;
    }

    public RobotScore getRobotScore(String botName) {
      for (RobotScore robotScore : _robotScores) {
        if (robotScore.botName.equals(botName)) {
          return robotScore;
        }
      }
      return null;
    }

    public RobotScore getRelativeTotalScore(String botName) {
      RobotScore referenceScore = getRobotScore(botName);
      List<RobotScore> enemyScores = Lists.newArrayList(_robotScores);
      enemyScores.remove(referenceScore);
      return referenceScore.getScoreRelativeTo(enemyScores, _numRounds);
    }
  }
}
