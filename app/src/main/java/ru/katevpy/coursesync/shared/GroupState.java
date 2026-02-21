package ru.katevpy.coursesync.shared;

import androidx.annotation.Nullable;
public class GroupState {
    @Nullable public final String groupNumber;

    public GroupState(@Nullable String groupNumber) {
        this.groupNumber = groupNumber;
    }

    public boolean hasGroup() {
        return groupNumber != null && !groupNumber.trim().isEmpty();
    }
}
