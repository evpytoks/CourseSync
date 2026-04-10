package ru.katevpy.coursesync.shared;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SharedGroupViewModel extends ViewModel {

    private final MutableLiveData<GroupState> groupState = new MutableLiveData<>(new GroupState(null, null, null));
    private final MutableLiveData<Boolean> ownerOfAnyGroup = new MutableLiveData<>(false);
    private final MutableLiveData<Set<String>> ownedGroupIds = new MutableLiveData<>(Collections.emptySet());

    public LiveData<GroupState> getGroupState() {
        return groupState;
    }

    public LiveData<Boolean> getOwnerOfAnyGroup() {
        return ownerOfAnyGroup;
    }

    public void setOwnerOfAnyGroup(boolean value) {
        ownerOfAnyGroup.setValue(value);
    }

    public LiveData<Set<String>> getOwnedGroupIds() {
        return ownedGroupIds;
    }

    public void setOwnedGroupIds(@Nullable Collection<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            ownedGroupIds.setValue(Collections.emptySet());
            return;
        }
        Set<String> next = new HashSet<>();
        for (String id : rawIds) {
            if (id != null && !id.trim().isEmpty()) {
                next.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        ownedGroupIds.setValue(Collections.unmodifiableSet(next));
    }

    public void setGroup(@Nullable String groupNumber, @Nullable String role, @Nullable String groupId) {
        groupState.setValue(new GroupState(groupNumber, role, groupId));
    }

    public void setGroup(@Nullable String groupNumber, @Nullable String role) {
        setGroup(groupNumber, role, null);
    }

    public void clearGroup() {
        groupState.setValue(new GroupState(null, null, null));
    }
}
