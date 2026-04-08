package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseContactMethodItem;

final class CourseContactsForm {

    private static final int MAX_PEOPLE = 10;
    private static final int MAX_METHODS_PER_PERSON = 10;
    private static final int NAME_MIN = 1;
    private static final int NAME_MAX = 100;
    private static final int TYPE_MAX = 100;
    private static final int VALUE_MIN = 1;
    private static final int VALUE_MAX = 200;

    private CourseContactsForm() {
    }

    static int validateForSubmit(@Nullable List<EditableContactPerson> people) {
        if (people == null || people.isEmpty()) {
            return R.string.contacts_need_at_least_one;
        }
        if (people.size() > MAX_PEOPLE) {
            return R.string.contacts_too_many_people;
        }
        for (EditableContactPerson personItem : people) {
            if (personItem == null) {
                return R.string.contacts_invalid_fields;
            }
            String person = trim(personItem.name);
            if (person.length() < NAME_MIN || person.length() > NAME_MAX) {
                return R.string.contact_person_name_invalid;
            }
            List<CourseContactMethodItem> methods = personItem.methods;
            if (methods == null || methods.isEmpty()) {
                return R.string.contacts_need_at_least_one_method;
            }
            if (methods.size() > MAX_METHODS_PER_PERSON) {
                return R.string.contacts_too_many_methods;
            }
            for (CourseContactMethodItem method : methods) {
                if (method == null) {
                    return R.string.contacts_invalid_fields;
                }
                String type = trim(method.type);
                String value = trim(method.value);
                if (type.length() > TYPE_MAX) {
                    return R.string.contact_method_type_invalid;
                }
                if (value.length() < VALUE_MIN || value.length() > VALUE_MAX) {
                    return R.string.contact_method_value_invalid;
                }
            }
        }
        return 0;
    }

    static boolean canAddTeacher(@Nullable List<EditableContactPerson> items) {
        return items == null || items.size() < MAX_PEOPLE;
    }

    static boolean canAddMethod(@Nullable EditableContactPerson person) {
        return person != null && person.methods.size() < MAX_METHODS_PER_PERSON;
    }

    static int validateDraftPersonName(@Nullable String person) {
        String p = trim(person);
        if (p.length() < NAME_MIN) {
            return R.string.contact_person_name_required;
        }
        if (p.length() > NAME_MAX) {
            return R.string.contact_person_name_invalid;
        }
        return 0;
    }

    static int validateDraftType(@Nullable String type) {
        String t = trim(type);
        if (t.length() > TYPE_MAX) {
            return R.string.contact_method_type_invalid;
        }
        return 0;
    }

    static int validateDraftValue(@Nullable String value) {
        String v = trim(value);
        if (v.length() < VALUE_MIN) {
            return R.string.contact_method_value_required;
        }
        if (v.length() > VALUE_MAX) {
            return R.string.contact_method_value_invalid;
        }
        return 0;
    }

    @NonNull
    static String trim(@Nullable String s) {
        return s != null ? s.trim() : "";
    }
}
