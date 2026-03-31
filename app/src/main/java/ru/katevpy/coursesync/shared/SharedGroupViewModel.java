package ru.katevpy.coursesync.shared;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedGroupViewModel extends ViewModel {

    private final MutableLiveData<GroupState> groupState = new MutableLiveData<>(new GroupState(null, null, null));
    private final MutableLiveData<Boolean> ownerOfAnyGroup = new MutableLiveData<>(false);

    public LiveData<GroupState> getGroupState() {
        return groupState;
    }

    public LiveData<Boolean> getOwnerOfAnyGroup() {
        return ownerOfAnyGroup;
    }

    public void setOwnerOfAnyGroup(boolean value) {
        ownerOfAnyGroup.setValue(value);
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
