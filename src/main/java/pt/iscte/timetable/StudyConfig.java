package pt.iscte.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit experiment inputs; missing real inputs are diagnosed, never guessed. */
public record StudyConfig(String scenario, boolean synthetic, String scheduleFile,
                          String roomsFile, String policyFile, LocalDate horizonStart, LocalDate horizonEnd,
                          int openingMinute, int closingMinute, int gridMinutes, Set<DayOfWeek> allowedDays,
                          Set<LocalDate> closedDates, boolean unavailableDatesReviewed,
                          boolean classIdentifiersReviewed, String multipleProfilesMode,
                          Map<String, TimeWindow> profileDefinitions, Map<String, List<String>> profilesByClass,
                          int populationSize, int maxEvaluations, int independentRuns, int numberOfCores,
                          List<Integer> problemSizes, Map<String, Double> softWeights, boolean allowIncompleteData) {

  public void validate() {
    if (scenario == null || scenario.isBlank()) throw new IllegalArgumentException("Missing scenario");
    if (gridMinutes <= 0 || openingMinute < 0 || closingMinute > 1440
            || openingMinute >= closingMinute || (closingMinute - openingMinute) % gridMinutes != 0)
      throw new IllegalArgumentException("Invalid opening hours or time grid");
    if ((horizonStart == null) != (horizonEnd == null)
            || horizonStart != null && horizonEnd.isBefore(horizonStart))
      throw new IllegalArgumentException("Invalid planning horizon");
    if (allowedDays == null || allowedDays.isEmpty() || closedDates == null
            || profileDefinitions == null || profilesByClass == null)
      throw new IllegalArgumentException("Missing calendar/profile configuration");
    if (multipleProfilesMode != null && !multipleProfilesMode.equals("SEPARATE_PERIODS"))
      throw new IllegalArgumentException("Only explicitly separate periods are implemented");
    if (populationSize < 2 || populationSize % 2 != 0 || maxEvaluations < populationSize
            || maxEvaluations % populationSize != 0 || maxEvaluations % 10 != 0
            || independentRuns < 2 || numberOfCores < 1 || problemSizes == null || problemSizes.isEmpty()
            || problemSizes.stream().anyMatch(n -> n == null || n <= 0)
            || problemSizes.stream().distinct().count() != problemSizes.size()
            || softWeights == null || !softWeights.keySet().equals(Set.of("type", "seats"))
            || softWeights.values().stream().anyMatch(w -> w == null || !Double.isFinite(w) || w < 0)
            || softWeights.values().stream().mapToDouble(Double::doubleValue).sum() == 0)
      throw new IllegalArgumentException("Invalid experiment parameters");
    for (var entry : profilesByClass.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isEmpty()
              || entry.getValue().stream().anyMatch(p -> !profileDefinitions.containsKey(p)))
        throw new IllegalArgumentException("Unknown/empty profile for " + entry.getKey());
    }
  }

  public boolean preferred(Lesson lesson, int start, int end) {
    return lesson.groups().stream().allMatch(group -> {
      var profiles = profilesByClass.get(group);
      if (profiles == null || profiles.isEmpty())
        throw new IllegalStateException("Unresolved profile: " + group);
      return profiles.stream().anyMatch(name -> profileDefinitions.get(name).contains(start, end));
    });
  }
}
