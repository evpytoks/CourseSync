package ru.katevpy.coursesync;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.repository.SettingsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private TextView tvGroupIndicator;
    private Button btnCreateCourse;
    private View toolbarGroupButtonsWrap;
    private Button btnCreateGroup;
    private Button btnJoinGroup;
    private NavController navController;
    private SharedGroupViewModel groupVm;
    private boolean appliedThemeFromServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        tvGroupIndicator = findViewById(R.id.tvGroupIndicator);
        btnCreateCourse = findViewById(R.id.btnCreateCourse);
        toolbarGroupButtonsWrap = findViewById(R.id.toolbarGroupButtonsWrap);
        btnCreateGroup = findViewById(R.id.btnCreateGroup);
        btnJoinGroup = findViewById(R.id.btnJoinGroup);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        navController = navHostFragment.getNavController();

        btnCreateCourse.setOnClickListener(v ->
                navController.navigate(R.id.action_coursesFragment_to_createCourseFragment));
        btnCreateGroup.setOnClickListener(v ->
                navController.navigate(R.id.action_groupsFragment_to_createGroupFragment));
        btnJoinGroup.setOnClickListener(v ->
                navController.navigate(R.id.action_groupsFragment_to_joinGroupFragment));

        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        groupVm = new ViewModelProvider(this).get(SharedGroupViewModel.class);

        groupVm.getGroupState().observe(this, state -> {
            if (tvGroupIndicator == null) return;

            if (state != null && state.hasGroup()) {
                tvGroupIndicator.setText(state.groupNumber);
            } else {
                tvGroupIndicator.setText("Нет группы");
            }

            if (btnCreateCourse != null && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.coursesFragment) {
                btnCreateCourse.setVisibility(state != null && state.hasGroup() ? View.VISIBLE : View.GONE);
            }
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            boolean isLogin = (id == R.id.loginFragment);
            boolean isCreateOrJoinGroup = (id == R.id.createGroupFragment || id == R.id.joinGroupFragment || id == R.id.editGroupFragment || id == R.id.createCourseFragment);
            boolean hideGroupIndicator = isLogin || isCreateOrJoinGroup || id == R.id.settingsFragment;

            if (isLogin) {
                appliedThemeFromServer = false;
            }

            bottomNav.setVisibility((isLogin || isCreateOrJoinGroup) ? View.GONE : View.VISIBLE);

            if (tvGroupIndicator != null) {
                tvGroupIndicator.setVisibility(hideGroupIndicator ? View.GONE : View.VISIBLE);
            }

            boolean isMainScreen = (id == R.id.groupsFragment || id == R.id.coursesFragment
                    || id == R.id.calendarFragment || id == R.id.settingsFragment);
            if (isMainScreen) {
                refreshCurrentGroup();
                if (!appliedThemeFromServer && App.getDeps().tokenStorage.getAccess() != null) {
                    appliedThemeFromServer = true;
                    refreshSettingsAndApplyTheme();
                }
            }

            boolean isGroupsScreen = (id == R.id.groupsFragment);
            boolean isCoursesScreen = (id == R.id.coursesFragment);

            if (btnCreateCourse != null) {
                if (isCoursesScreen) {
                    GroupState state = groupVm.getGroupState().getValue();
                    btnCreateCourse.setVisibility(state != null && state.hasGroup() ? View.VISIBLE : View.GONE);
                } else {
                    btnCreateCourse.setVisibility(View.GONE);
                }
            }
            if (toolbarGroupButtonsWrap != null) {
                toolbarGroupButtonsWrap.setVisibility(isGroupsScreen ? View.VISIBLE : View.GONE);
            }

            if (toolbar != null && getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        });
    }

    public void setCurrentGroupName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            groupVm.setGroup(name.trim());
        } else {
            groupVm.clearGroup();
        }
    }

    public void refreshCurrentGroup() {
        GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
        new Thread(() -> {
            Result<GroupDetailsResponse> result = repo.getCurrentGroup();
            runOnUiThread(() -> {
                if (result instanceof Result.Success && ((Result.Success<GroupDetailsResponse>) result).data != null) {
                    GroupDetailsResponse data = ((Result.Success<GroupDetailsResponse>) result).data;
                    if (data.name != null && !data.name.trim().isEmpty()) {
                        groupVm.setGroup(data.name.trim());
                        return;
                    }
                }
                groupVm.clearGroup();
            });
        }).start();
    }

    private void refreshSettingsAndApplyTheme() {
        SettingsRepository repo = new SettingsRepository(App.getDeps().settingsApi);
        new Thread(() -> {
            Result<UserSettingsResponse> result = repo.getSettings();
            runOnUiThread(() -> {
                if (result instanceof Result.Success && ((Result.Success<UserSettingsResponse>) result).data != null) {
                    UserSettingsResponse data = ((Result.Success<UserSettingsResponse>) result).data;
                    AppCompatDelegate.setDefaultNightMode(
                            data.darkThemeOn ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                    );
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            });
        }).start();
    }
}