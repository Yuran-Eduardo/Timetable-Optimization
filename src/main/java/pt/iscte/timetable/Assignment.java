package pt.iscte.timetable;

import java.time.LocalDate;

/** Representa a sala atribuída a uma aula, com a data e o intervalo em minutos. */
public record Assignment(int lessonIndex, LocalDate date, int start, int end, Room room) {}
