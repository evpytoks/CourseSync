package ru.katevpy.coursesync.shared;

import androidx.annotation.Nullable;

public class GroupState {
    @Nullable public final String groupNumber;
    @Nullable public final String role;

    public GroupState(@Nullable String groupNumber, @Nullable String role) {
        this.groupNumber = groupNumber;
        this.role = role;
    }

    public boolean hasGroup() {
        return groupNumber != null && !groupNumber.trim().isEmpty();
    }

    public boolean isGroupOwner() {
        if (role == null) {
            return false;
        }
        return "owner".equalsIgnoreCase(role.trim());
    }
}
