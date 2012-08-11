package robowiki.runner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
  private static final String ROUNDS = "rounds";
  private static final String SURVIVAL = "survival";
  private static final String DAMAGE = "damage";
  private static final String ENERGY = "energy";
  private static final String TIME = "time";
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private static XMLEventFactory XML_EVENT_FACTORY =
      XMLEventFactory.newInstance();
  private static XMLEvent XML_TAB = XML_EVENT_FACTORY.createDTD("\t");
  private static XMLEvent XML_NL = XML_EVENT_FACTORY.createDTD("\n");

  public final String botName;
  private Map<String, List<BattleScore>> _scores;

  public ScoreLog(String botName) {
    this.botName = botName;
    _scores = Maps.newHashMap();
  }

  public void addBattle(List<RobotScore> robotScores, long nanoTime) {
    String botListString = getSortedBotList(robotScores, botName);
    if (!_scores.containsKey(botListString)) {
      _scores.put(botListString, Lists.<BattleScore>newArrayList());
    }
    _scores.get(botListString).add(new BattleScore(robotScores, nanoTime));
  }

  private String getSortedBotList(
      List<RobotScore> robotScores, String challengerBot) {
    List<String> botList = Lists.newArrayList(Lists.transform(robotScores,
        new Function<RobotScore, String>() {
          @Override
          public String apply(RobotScore robotScore) {
            return robotScore.botName;
          }
        }));
    botList.remove(challengerBot);
    Collections.sort(botList);
    return COMMA_JOINER.join(botList);
  }

  /**
   * Reads in the scores from an XML data file and creates a new
   * {@code ScoreLog} with the battle data.
   *
   * @param inputFilePath path of the XML data file
   * @return a new {@code ScoreLog} with the scores from the input file
   */
  public static ScoreLog loadScoreLog(String inputFilePath) {
    try {
      ScoreLog scoreLog = null;
      List<RobotScore> robotScores = null;
      long time = 0;

      XMLEventReader eventReader =
          XMLInputFactory.newInstance().createXMLEventReader(
              new FileInputStream(inputFilePath));
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
          } else if (localPart.equals(TIME)) {
            event = eventReader.nextEvent();
            time = Long.parseLong(event.asCharacters().getData());
          }
        } else if (event.isEndElement()) {
          String localPart = event.asEndElement().getName().getLocalPart();
          if (localPart.equals(BATTLE)) {
            scoreLog.addBattle(robotScores, time);
          }
        }
      }
      return scoreLog;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (XMLStreamException e) {
      e.printStackTrace();
    }
    return null;
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
    double energy = 0;
    while (true) {
      XMLEvent event = eventReader.nextEvent();
      if (event.isEndElement()
          && event.asEndElement().getName().getLocalPart().equals(
              ROBOT_SCORE)) {
        return new RobotScore(name, score, rounds, survival, damage, energy);
      } else {
        if (event.isStartElement()) {
          String localPart = event.asStartElement().getName().getLocalPart();
          event = eventReader.nextEvent();
          if (localPart.equals(NAME)) {
            name = event.asCharacters().getData();
          } else if (localPart.equals(SCORE)) {
            score = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(ROUNDS)) {
            rounds = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(SURVIVAL)) {
            survival = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(DAMAGE)) {
            damage = Double.parseDouble(event.asCharacters().getData());
          } else if (localPart.equals(ENERGY)) {
            energy = Double.parseDouble(event.asCharacters().getData());
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
    try {
      XMLEventWriter eventWriter =
          XMLOutputFactory.newInstance().createXMLEventWriter(
              new FileOutputStream(outputFilePath));
      eventWriter.add(XML_EVENT_FACTORY.createStartDocument());
      eventWriter.add(XML_NL);
      writeStartElement(
          eventWriter, SCORES, createAttributes(CHALLENGER, botName), 0);

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
            writeValue(eventWriter, SCORE, robotScore.score, 4);
            writeValue(eventWriter, ROUNDS, robotScore.survivalRounds, 4);
            writeValue(eventWriter, SURVIVAL, robotScore.survivalScore, 4);
            writeValue(eventWriter, DAMAGE, robotScore.bulletDamage, 4);
            writeValue(eventWriter, ENERGY, robotScore.energyConserved, 4);
            writeEndElement(eventWriter, ROBOT_SCORE, 3);
          }
          writeValue(eventWriter, TIME,
              Long.toString(battleScore.getNanoTime()), 3);
          writeEndElement(eventWriter, BATTLE, 2);
        }
        writeEndElement(eventWriter, botList, 1);
      }

      writeEndElement(eventWriter, SCORES, 0);
      eventWriter.add(XML_EVENT_FACTORY.createEndDocument());
      eventWriter.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (XMLStreamException e) {
      e.printStackTrace();
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

  private void writeValue(XMLEventWriter eventWriter, String name, double value,
      int numTabs) throws XMLStreamException {
    writeValue(eventWriter, name, Double.toString(value), numTabs);
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
    private final long _elapsedTime;

    public BattleScore(Collection<RobotScore> scores, long nanoTime) {
      _robotScores = ImmutableList.copyOf(scores);
      _elapsedTime = nanoTime;
    }

    public List<RobotScore> getRobotScores() {
      return _robotScores;
    }

    public long getNanoTime() {
      return _elapsedTime;
    }
  }
}
