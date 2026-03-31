package ru.katevpy.coursesync;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import ru.katevpy.coursesync.ui.ErrorUi;

import ru.katevpy.coursesync.calendar.EventDetailToolbarViewModel;
import ru.katevpy.coursesync.calendar.EventDetailToolbarViewModelFactory;
import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.repository.SettingsRepository;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_NEWS_FROM_PUSH = "coursesync_open_news_list";

    private BottomNavigationView bottomNav;
    private View groupIndicatorContainer;
    private TextView tvGroupIndicator;
    private ImageButton btnEditCourse;
    private ImageButton btnToolbarCreate;
    private ImageButton btnToolbarGroup;
    private ImageButton btnEditEvent;
    private NavController navController;
    private SharedGroupViewModel groupVm;
    private EventDetailToolbarViewModel eventDetailToolbarVm;
    private boolean appliedThemeFromServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        groupIndicatorContainer = findViewById(R.id.groupIndicatorContainer);
        tvGroupIndicator = findViewById(R.id.tvGroupIndicator);
        btnEditCourse = findViewById(R.id.btnEditCourse);
        btnToolbarCreate = findViewById(R.id.btnToolbarCreate);
        btnToolbarGroup = findViewById(R.id.btnToolbarGroup);
        btnEditEvent = findViewById(R.id.btnEditEvent);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        navController = navHostFragment.getNavController();

        eventDetailToolbarVm = new ViewModelProvider(this, new EventDetailToolbarViewModelFactory()).get(EventDetailToolbarViewModel.class);
        eventDetailToolbarVm.getDeleteResult().observe(this, this::onEventDeleteResult);

        btnToolbarCreate.setOnClickListener(v -> {
            if (navController.getCurrentDestination() == null) return;
            int destId = navController.getCurrentDestination().getId();
            if (destId == R.id.newsFragment) {
                navController.navigate(R.id.action_newsFragment_to_createNewsFragment);
            } else if (destId == R.id.calendarFragment) {
                navController.navigate(R.id.action_calendarFragment_to_createCalendarEventFragment);
            } else if (destId == R.id.coursesFragment) {
                navController.navigate(R.id.action_coursesFragment_to_createCourseFragment);
            }
        });
        btnToolbarGroup.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.toolbar_group_actions, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_create_group) {
                    navController.navigate(R.id.action_groupsFragment_to_createGroupFragment);
                    return true;
                }
                if (itemId == R.id.action_join_group) {
                    navController.navigate(R.id.action_groupsFragment_to_joinGroupFragment);
                    return true;
                }
                return false;
            });
            popup.show();
        });

        btnEditCourse.setOnClickListener(v -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.courseDetailFragment
                    && navController.getCurrentBackStackEntry() != null
                    && navController.getCurrentBackStackEntry().getArguments() != null) {
                String courseId = navController.getCurrentBackStackEntry().getArguments().getString("courseId");
                if (courseId != null && !courseId.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("courseId", courseId);
                    navController.navigate(R.id.action_courseDetailFragment_to_editCourseFragment, args);
                }
            }
        });

        btnEditEvent.setOnClickListener(v -> {
            if (navController.getCurrentBackStackEntry() != null && navController.getCurrentBackStackEntry().getArguments() != null) {
                String eventId = navController.getCurrentBackStackEntry().getArguments().getString("eventId");
                if (eventId != null && !eventId.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("eventId", eventId);
                    navController.navigate(R.id.action_calendarEventDetailFragment_to_editCalendarEventFragment, args);
                }
            }
        });

        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        applyEdgeToEdgeWindowInsets(toolbar, bottomNav);

        groupVm = new ViewModelProvider(this).get(SharedGroupViewModel.class);

        groupVm.getGroupState().observe(this, state -> {
            if (tvGroupIndicator == null) return;

            if (state != null && state.hasGroup()) {
                tvGroupIndicator.setText(state.groupNumber);
            } else {
                tvGroupIndicator.setText("Нет группы");
            }

            syncToolbarCreateButton();
            if (btnEditCourse != null && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.courseDetailFragment) {
                btnEditCourse.setVisibility(showOwnerToolbarActions(state) ? View.VISIBLE : View.GONE);
            }
            if (btnEditEvent != null && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.calendarEventDetailFragment) {
                btnEditEvent.setVisibility(showOwnerToolbarActions(state) ? View.VISIBLE : View.GONE);
            }
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            boolean isLogin = (id == R.id.loginFragment);
            boolean isCreateOrJoinGroup = (id == R.id.createGroupFragment || id == R.id.joinGroupFragment || id == R.id.editGroupFragment || id == R.id.createCourseFragment || id == R.id.createNewsFragment || id == R.id.createCalendarEventFragment || id == R.id.calendarEventDetailFragment || id == R.id.editCalendarEventFragment || id == R.id.newsDetailFragment || id == R.id.editCourseFragment || id == R.id.editCourseGradingFormulaFragment);
            boolean isEventScreen = (id == R.id.createCalendarEventFragment || id == R.id.calendarEventDetailFragment || id == R.id.editCalendarEventFragment);
            boolean isNewsScreenWithGroup = (id == R.id.newsDetailFragment || id == R.id.createNewsFragment);
            boolean isCourseScreenWithGroup = (id == R.id.courseDetailFragment || id == R.id.editCourseFragment || id == R.id.editCourseGradingFormulaFragment);
            boolean hideGroupIndicator = isLogin || id == R.id.settingsFragment || (isCreateOrJoinGroup && !isEventScreen && !isNewsScreenWithGroup && !isCourseScreenWithGroup);

            if (isLogin) {
                appliedThemeFromServer = false;
            }

            bottomNav.setVisibility((isLogin || isCreateOrJoinGroup) ? View.GONE : View.VISIBLE);

            if (groupIndicatorContainer != null) {
                groupIndicatorContainer.setVisibility(hideGroupIndicator ? View.GONE : View.VISIBLE);
            }

            boolean isMainScreen = (id == R.id.groupsFragment || id == R.id.newsFragment || id == R.id.coursesFragment
                    || id == R.id.calendarFragment);
            if (isMainScreen) {
                refreshCurrentGroup();
                if (!appliedThemeFromServer && App.getDeps().tokenStorage.getAccess() != null) {
                    appliedThemeFromServer = true;
                    refreshSettingsAndApplyTheme();
                }
            }
            if (id == R.id.courseDetailFragment || id == R.id.courseGradingFormulaFragment) {
                refreshCurrentGroup();
            }

            boolean isGroupsScreen = (id == R.id.groupsFragment);

            GroupState groupState = groupVm.getGroupState().getValue();
            syncToolbarCreateButton();
            if (btnEditCourse != null) {
                btnEditCourse.setVisibility(id == R.id.courseDetailFragment && showOwnerToolbarActions(groupState)
                        ? View.VISIBLE : View.GONE);
            }
            if (btnToolbarGroup != null) {
                btnToolbarGroup.setVisibility(isGroupsScreen ? View.VISIBLE : View.GONE);
            }
            boolean isEventDetailScreen = (id == R.id.calendarEventDetailFragment);
            if (btnEditEvent != null) {
                btnEditEvent.setVisibility(
                        isEventDetailScreen && showOwnerToolbarActions(groupState) ? View.VISIBLE : View.GONE);
            }

            if (toolbar != null && getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        });

        if (savedInstanceState == null) {
            handleNotificationIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || !shouldOpenNewsListFromNotification(intent)) return;
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor != null) {
            decor.post(this::openNewsListFromPush);
        }
    }

    private static boolean shouldOpenNewsListFromNotification(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_NEWS_FROM_PUSH, false)) {
            return true;
        }
        String type = intent.getStringExtra("type");
        return type != null && !type.isEmpty();
    }

    private void openNewsListFromPush() {
        if (navController == null) return;
        String token = App.getDeps().tokenStorage.getAccess();
        if (token == null || token.isEmpty()) {
            ((App) getApplication()).pendingOpenNewsListFromNotification = true;
            return;
        }
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.loginFragment) {
            navController.navigate(R.id.action_loginFragment_to_groupsFragment);
        }
        scheduleOpenNewsTab();
    }

    public void scheduleOpenNewsTab() {
        if (bottomNav == null) return;
        bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.newsFragment));
    }

    public void setCurrentGroupName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            groupVm.setGroup(name.trim(), null);
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
                        groupVm.setGroup(data.name.trim(), data.role);
                        return;
                    }
                }
                groupVm.clearGroup();
            });
        }).start();
    }

    private static boolean showOwnerToolbarActions(@Nullable GroupState state) {
        return state != null && state.hasGroup() && state.isGroupOwner();
    }

    private static void applyEdgeToEdgeWindowInsets(MaterialToolbar toolbar, BottomNavigationView bottomNav) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
            Insets topBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), topBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, windowInsets) -> {
            Insets navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(toolbar);
        ViewCompat.requestApplyInsets(bottomNav);
    }

    private void syncToolbarCreateButton() {
        if (btnToolbarCreate == null || navController == null || navController.getCurrentDestination() == null) {
            return;
        }
        int destId = navController.getCurrentDestination().getId();
        boolean onListTab = destId == R.id.coursesFragment
                || destId == R.id.newsFragment
                || destId == R.id.calendarFragment;
        GroupState gs = groupVm != null ? groupVm.getGroupState().getValue() : null;
        if (onListTab && showOwnerToolbarActions(gs)) {
            btnToolbarCreate.setVisibility(View.VISIBLE);
            if (destId == R.id.coursesFragment) {
                btnToolbarCreate.setContentDescription(getString(R.string.create_course));
            } else if (destId == R.id.newsFragment) {
                btnToolbarCreate.setContentDescription(getString(R.string.add_news));
            } else {
                btnToolbarCreate.setContentDescription(getString(R.string.add_event));
            }
        } else {
            btnToolbarCreate.setVisibility(View.GONE);
        }
    }

    private void onEventDeleteResult(@Nullable Result<Void> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            navController.navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                navController.navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, findViewById(android.R.id.content), R.string.delete_event_server_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, findViewById(android.R.id.content), R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void refreshSettingsAndApplyTheme() {
        SettingsRepository repo = new SettingsRepository(App.getDeps().settingsApi);
        new Thread(() -> {
            Result<UserSettingsResponse> result = repo.getSettings();
            runOnUiThread(() -> {
                if (result instanceof Result.Success && ((Result.Success<UserSettingsResponse>) result).data != null) {
                    UserSettingsResponse data = ((Result.Success<UserSettingsResponse>) result).data;
                    int targetMode = data.darkThemeOn ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                    if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                        AppCompatDelegate.setDefaultNightMode(targetMode);
                    }
                }
            });
        }).start();
    }
}