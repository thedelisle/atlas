package atlas;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.*;

public class CityIndex implements Serializable {

  enum Direction {
    UP, DOWN, RIGHT, LEFT
  }

  public static final String DATA_FOLDER_NAME = "./data";
  public static final String ADMIN1_DATA_FILE_NAME = "admin1CodesASCII.txt";
  public static final String ADMIN2_DATA_FILE_NAME = "admin2Codes.txt";
  public static final String CITY_DATA_FILE_NAME = "cities1000.txt";

  public static final String INDEX_FOLDER_NAME = "./src/main/resources";
  public static final String INDEX_FILE_NAME = "index.ser";

  private final NavigableMap<Double, Set<City>> latitudeIndex;
  private final NavigableMap<Double, Set<City>> longitudeIndex;

  @SuppressWarnings("unused")
  public CityIndex() {
    // Public no-args constructor needed for serialization lib.
    this(new HashSet<City>());
  }

  protected CityIndex(Set<City> locations) {

    this.latitudeIndex = new TreeMap<>();
    this.longitudeIndex = new TreeMap<>();

    for (City location : locations) {
      this.insert(location);
    }
  }

  protected void insert(City location) {
    insert(location.latitude, location, this.latitudeIndex);
    insert(location.longitude, location, this.longitudeIndex);
  }

  protected void insert(Double key, City location, NavigableMap<Double, Set<City>> index) {
    Set<City> locations = index.remove(key);

    if (locations == null) {
      locations = new LinkedHashSet<>();
    }

    locations.add(location);
    index.put(key, locations);
  }

  protected City nearestNeighbour(double latitude, double longitude, double maxDistance) {
    List<City> hits = this.nearestNeighbours(latitude, longitude, maxDistance, 1);
    return hits.isEmpty() ? null : hits.get(0);
  }

  protected List<City> nearestNeighbours(double latitude, double longitude, double maxDistance, int maxHits) {

    City center = new City(latitude, longitude);
    SortedSet<City> hits = new TreeSet<>(new DistanceComparator(center));

    // Query up and down on the latitude-axis.
    fillHits(center, hits, this.latitudeIndex.floorEntry(center.latitude), this.latitudeIndex, Direction.DOWN, maxDistance);
    fillHits(center, hits, this.latitudeIndex.ceilingEntry(center.latitude), this.latitudeIndex, Direction.UP, maxDistance);

    // Query left and right on the longitude-axis.
    fillHits(center, hits, this.longitudeIndex.floorEntry(center.longitude), this.longitudeIndex, Direction.LEFT, maxDistance);
    fillHits(center, hits, this.longitudeIndex.ceilingEntry(center.longitude), this.longitudeIndex, Direction.RIGHT, maxDistance);

    return hits.size() <= maxHits ? new ArrayList<>(hits) : new ArrayList<>(hits).subList(0, maxHits);
  }

  private void fillHits(City center, Set<City> hits, Map.Entry<Double, Set<City>> entry,
                        NavigableMap<Double, Set<City>> index, Direction direction, double maxDistance) {
    while (true) {

      if (entry == null || entry.getValue() == null) {
        return;
      }

      for (City hit : entry.getValue()) {
        if (center.distanceTo(hit) <= maxDistance) {
          hits.add(hit);
        }
      }

      City location = getLocationFrom(entry);
      City next;

      switch (direction) {
        case UP:
          entry = index.higherEntry(location.latitude);
          next = new City(getLocationFrom(entry).latitude, center.longitude);
          break;
        case DOWN:
          entry = index.lowerEntry(location.latitude);
          next = new City(getLocationFrom(entry).latitude, center.longitude);
          break;
        case RIGHT:
          entry = index.higherEntry(location.longitude);
          next = new City(center.latitude, getLocationFrom(entry).longitude);
          break;
        default:
          entry = index.lowerEntry(location.longitude);
          next = new City(center.latitude, getLocationFrom(entry).longitude);
          break;
      }

      if (center.distanceTo(next) > maxDistance) {
        return;
      }
    }
  }

  // Utility method that reads a single location from a map-entry.
  private City getLocationFrom(Map.Entry<Double, Set<City>> entry) {

    if (entry == null || entry.getValue().isEmpty()) {
      // Should not happen.
      throw new RuntimeException("corrupt index: recreate the index");
    }

    return new ArrayList<>(entry.getValue()).get(0);
  }

  /**
   * Reads all locations and serializes this index which will be included
   * in the JAR file after doing a {@code mvn package}
   *
   * @param args an optional comma separated list of country codes to
   *             include in the index. When no parameter is used, all
   *             locations will be imported.
   *
   * @throws FileNotFoundException when the file with geo-locations couldn't
   * be found: ./{@value #INDEX_FOLDER_NAME}/{@value #CITY_DATA_FILE_NAME}
   */
  public static void main(String[] args) throws FileNotFoundException{

    Set<String> countryCodes = new HashSet<>();

    if (args.length > 0) {
      countryCodes.addAll(Arrays.asList(args[0].toUpperCase().split(",")));
    }

    if (countryCodes.isEmpty()) {
      System.out.printf("Reading all locations from %s/%s\n", DATA_FOLDER_NAME, ADMIN1_DATA_FILE_NAME);
    }
    else {
      System.out.printf("Reading locations with country codes %s from %s/%s\n", countryCodes, DATA_FOLDER_NAME, ADMIN1_DATA_FILE_NAME);
    }

    Map<String, String> adminMap = new LinkedHashMap<>();

    adminMap.putAll(Utils.read(new File(DATA_FOLDER_NAME, ADMIN1_DATA_FILE_NAME), "\t"));
    adminMap.putAll(Utils.read(new File(DATA_FOLDER_NAME, ADMIN2_DATA_FILE_NAME), "\t"));

    Scanner scanner = new Scanner(new File(DATA_FOLDER_NAME, CITY_DATA_FILE_NAME));
    Set<City> locations = new LinkedHashSet<>();

    while (scanner.hasNextLine()) {

      String line = scanner.nextLine();
      City location = new City(line, adminMap);

      if (countryCodes.isEmpty() || countryCodes.contains(location.countryCode.toUpperCase())) {
        locations.add(location);
      }
    }

    System.out.printf("Finished loading %s locations, writing index to disk...\n", locations.size());

    Utils.serialize(new CityIndex(locations), new File(INDEX_FOLDER_NAME, INDEX_FILE_NAME));

    System.out.printf("OK!\nRun `mvn package` to create a JAR file containing the newly created index.\n");
  }
}