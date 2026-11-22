package pt.iscte.timetable;

import java.time.LocalTime;

/** Representa um intervalo horário usado nos diagnósticos de preferências. */
public record TimeWindow(LocalTime start, LocalTime end) {
  /** Exige um intervalo não vazio, com início anterior ao fim. */
  public TimeWindow {
    if (start == null || end == null || !start.isBefore(end))
      throw new IllegalArgumentException("Invalid preferred time window");
  }
  /** Verifica se o intervalo indicado cabe integralmente nesta janela horária. */
  public boolean contains(int from, int to) {
    return from >= start.toSecondOfDay() / 60 && to <= end.toSecondOfDay() / 60;
  }
}
