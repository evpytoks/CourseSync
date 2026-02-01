package ru.katevpy.coursesync.login;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LoginViewModel extends ViewModel {

    public enum Step { ENTER_EMAIL, ENTER_CODE }

    private final MutableLiveData<Step> step = new MutableLiveData<>(Step.ENTER_EMAIL);

    public LiveData<Step> getStep() {
        return step;
    }

    public void onSendCodeClicked() {
        step.setValue(Step.ENTER_CODE);
    }
}

