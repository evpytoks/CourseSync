package ru.katevpy.coursesync.courses;

import java.util.ArrayList;
import java.util.List;

import ru.katevpy.coursesync.shared.dto.CourseContactMethodItem;

final class EditableContactPerson {
    String name;
    final List<CourseContactMethodItem> methods = new ArrayList<>();

    EditableContactPerson(String name) {
        this.name = name;
    }
}
