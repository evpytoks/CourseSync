package ru.katevpy.coursesync.shared;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedGroupViewModel extends ViewModel {

    private final MutableLiveData<GroupState> groupState = new MutableLiveData<>(new GroupState(null, null));

    public LiveData<GroupState> getGroupState() {
        return groupState;
    }

    public void setGroup(@Nullable String groupNumber, @Nullable String role) {
        groupState.setValue(new GroupState(groupNumber, role));
    }

    public void clearGroup() {
        groupState.setValue(new GroupState(null, null));
    }
}
