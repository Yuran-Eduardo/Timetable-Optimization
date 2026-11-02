package pt.iscte.timetable;

import java.time.LocalDate;

public record Assignment(int lessonIndex, LocalDate date, int start, int end, Room room) {}
