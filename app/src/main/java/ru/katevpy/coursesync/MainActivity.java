package ru.katevpy.coursesync;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
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
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import ru.katevpy.coursesync.ui.ErrorUi;

import ru.katevpy.coursesync.push.CourseSyncFirebaseMessagingService;
import ru.katevpy.coursesync.calendar.EventDetailToolbarViewModel;
import ru.katevpy.coursesync.calendar.EventDetailToolbarViewModelFactory;
import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.GroupListItem;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListItem;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    @Nullable
    private ListPopupWindow toolbarGroupListPopup;
    private int previousNavDestinationId;

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
        groupIndicatorContainer.setContentDescription(getString(R.string.toolbar_group_switch_content_description));
        groupIndicatorContainer.setOnClickListener(v -> openToolbarGroupPicker());
        btnEditCourse = findViewById(R.id.btnEditCourse);
        btnToolbarCreate = findViewById(R.id.btnToolbarCreate);
        btnToolbarGroup = findViewById(R.id.btnToolbarGroup);
        btnEditEvent = findViewById(R.id.btnEditEvent);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        navController = navHostFragment.getNavController();

        eventDetailToolbarVm = new ViewModelProvider(this, new EventDetailToolbarViewModelFactory()).get(EventDetailToolbarViewModel.class);
        eventDetailToolbarVm.getDeleteResult().observe(this, this::onEventDeleteResult);
        eventDetailToolbarVm.getEventEditAllowed().observe(this, this::syncCalendarEventEditButton);

        btnToolbarCreate.setOnClickListener(v -> {
            if (navController.getCurrentDestination() == null) return;
            int destId = navController.getCurrentDestination().getId();
            if (destId == R.id.newsFragment) {
                navController.navigate(R.id.action_newsFragment_to_createNewsFragment);
            } else if (destId == R.id.calendarFragment) {
                View snackRoot = findViewById(android.R.id.content);
                new Thread(() -> {
                    GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
                    Result<OwnerGroupListResponse> ownerResult = repo.getOwnerGroups();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (!(ownerResult instanceof Result.Success)) {
                            ErrorUi.show(MainActivity.this, snackRoot, R.string.internal_error, ErrorUi.Duration.SHORT);
                            return;
                        }
                        OwnerGroupListResponse payload = ((Result.Success<OwnerGroupListResponse>) ownerResult).data;
                        List<OwnerGroupListItem> owned = payload != null ? payload.groups : null;
                        if (owned == null || owned.isEmpty()) {
                            ErrorUi.show(MainActivity.this, snackRoot, R.string.calendar_create_need_owner_group, ErrorUi.Duration.SHORT);
                            return;
                        }
                        navController.navigate(R.id.action_calendarFragment_to_createCalendarEventFragment);
                    });
                }).start();
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

        groupVm.getOwnerOfAnyGroup().observe(this, v -> syncToolbarCreateButton());

        groupVm.getGroupState().observe(this, state -> {
            if (tvGroupIndicator == null) return;

            String nextLabel;
            if (state != null && state.hasGroup()) {
                nextLabel = state.groupNumber != null ? state.groupNumber.trim() : "";
                if (nextLabel.isEmpty()) {
                    nextLabel = getString(R.string.toolbar_no_group_label);
                }
            } else {
                nextLabel = getString(R.string.toolbar_no_group_label);
            }
            if (!nextLabel.contentEquals(tvGroupIndicator.getText())) {
                tvGroupIndicator.setText(nextLabel);
            }

            syncToolbarCreateButton();
            if (btnEditCourse != null && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.courseDetailFragment) {
                btnEditCourse.setVisibility(showOwnerToolbarActions(state) ? View.VISIBLE : View.GONE);
            }
            syncCalendarEventEditButton();
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            boolean isLogin = (id == R.id.loginFragment);
            boolean isCreateOrJoinGroup = (id == R.id.createGroupFragment || id == R.id.joinGroupFragment || id == R.id.editGroupFragment || id == R.id.groupMembersFragment || id == R.id.createCourseFragment || id == R.id.createNewsFragment || id == R.id.createCalendarEventFragment || id == R.id.calendarEventDetailFragment || id == R.id.editCalendarEventFragment || id == R.id.newsDetailFragment || id == R.id.editCourseFragment || id == R.id.editCourseGradingFormulaFragment);
            boolean isEventScreen = (id == R.id.createCalendarEventFragment || id == R.id.calendarEventDetailFragment || id == R.id.editCalendarEventFragment);
            boolean isNewsScreenWithGroup = (id == R.id.newsDetailFragment || id == R.id.createNewsFragment);
            boolean isCourseScreenWithGroup = (id == R.id.courseDetailFragment || id == R.id.editCourseFragment || id == R.id.editCourseGradingFormulaFragment);
            boolean isCalendarFlow = (id == R.id.calendarFragment || id == R.id.createCalendarEventFragment
                    || id == R.id.calendarEventDetailFragment || id == R.id.editCalendarEventFragment);
            boolean hideGroupIndicator = isLogin || id == R.id.settingsFragment || id == R.id.newsFragment
                    || id == R.id.newsDetailFragment || id == R.id.groupsFragment
                    || isCalendarFlow
                    || (isCreateOrJoinGroup && !isEventScreen && !isNewsScreenWithGroup && !isCourseScreenWithGroup);

            if (isLogin) {
                appliedThemeFromServer = false;
                updateNewsUnreadBadge(0);
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
            if (previousNavDestinationId == R.id.calendarEventDetailFragment
                    && id != R.id.calendarEventDetailFragment) {
                eventDetailToolbarVm.clearEventEditAllowed();
            }
            previousNavDestinationId = id;
            syncCalendarEventEditButton();

            if (toolbar != null && getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        });

        if (savedInstanceState == null) {
            tryEnterAppIfSessionPresent();
            handleNotificationIntent(getIntent());
        }
    }

    private void tryEnterAppIfSessionPresent() {
        String refresh = App.getDeps().tokenStorage.getRefresh();
        if (refresh == null || refresh.trim().isEmpty()) {
            return;
        }
        navController.navigate(R.id.action_loginFragment_to_groupsFragment);
        CourseSyncFirebaseMessagingService.registerDeviceAfterLogin(getApplicationContext());
        App app = (App) getApplication();
        if (app.pendingOpenNewsListFromNotification) {
            app.pendingOpenNewsListFromNotification = false;
            scheduleOpenNewsTab();
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

    public void scheduleOpenCoursesTab() {
        if (bottomNav == null) {
            return;
        }
        bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.coursesFragment));
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
            Result<OwnerGroupListResponse> ownerResult = repo.getOwnerGroups();
            runOnUiThread(() -> {
                boolean ownsAny = false;
                if (ownerResult instanceof Result.Success) {
                    OwnerGroupListResponse og = ((Result.Success<OwnerGroupListResponse>) ownerResult).data;
                    ownsAny = og != null && og.groups != null && !og.groups.isEmpty();
                    List<String> ownedIdStrings = new ArrayList<>();
                    if (og != null && og.groups != null) {
                        for (OwnerGroupListItem it : og.groups) {
                            if (it != null && it.id != null && !it.id.trim().isEmpty()) {
                                ownedIdStrings.add(it.id);
                            }
                        }
                    }
                    groupVm.setOwnedGroupIds(ownedIdStrings);
                }
                groupVm.setOwnerOfAnyGroup(ownsAny);

                if (result instanceof Result.Success && ((Result.Success<GroupDetailsResponse>) result).data != null) {
                    GroupDetailsResponse data = ((Result.Success<GroupDetailsResponse>) result).data;
                    if (data.name != null && !data.name.trim().isEmpty()) {
                        groupVm.setGroup(data.name.trim(), data.role, data.id);
                        return;
                    }
                }
                groupVm.clearGroup();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        dismissToolbarGroupPicker();
        super.onDestroy();
    }

    private void dismissToolbarGroupPicker() {
        if (toolbarGroupListPopup != null) {
            try {
                toolbarGroupListPopup.dismiss();
            } catch (Exception ignored) {
            }
            toolbarGroupListPopup = null;
        }
    }

    private void openToolbarGroupPicker() {
        if (groupIndicatorContainer == null || groupIndicatorContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        String token = App.getDeps().tokenStorage.getAccess();
        if (token == null || token.isEmpty()) {
            return;
        }
        dismissToolbarGroupPicker();
        new Thread(() -> {
            GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
            Result<GroupListResponse> r = repo.getGroups();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!(r instanceof Result.Success)) {
                    onToolbarGroupListLoadFailed(r);
                    return;
                }
                GroupListResponse payload = ((Result.Success<GroupListResponse>) r).data;
                List<GroupListItem> raw = payload != null ? payload.items : null;
                List<GroupListItem> items = new ArrayList<>();
                if (raw != null) {
                    for (GroupListItem it : raw) {
                        if (it != null && it.id != null) {
                            items.add(it);
                        }
                    }
                }
                if (items.isEmpty()) {
                    ErrorUi.show(this, findViewById(android.R.id.content), R.string.no_groups, ErrorUi.Duration.SHORT);
                    return;
                }
                showToolbarGroupListPopup(items);
            });
        }).start();
    }

    private void onToolbarGroupListLoadFailed(@Nullable Result<GroupListResponse> r) {
        if (r instanceof Result.HttpError) {
            int code = ((Result.HttpError<GroupListResponse>) r).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                navigateToLoginClearingStack();
                return;
            }
        }
        if (r instanceof Result.NetworkError) {
            ErrorUi.show(this, findViewById(android.R.id.content), R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, findViewById(android.R.id.content), R.string.groups_load_error, ErrorUi.Duration.SHORT);
    }

    private void showToolbarGroupListPopup(List<GroupListItem> items) {
        dismissToolbarGroupPicker();
        List<CharSequence> labels = new ArrayList<>(items.size());
        for (GroupListItem g : items) {
            labels.add(buildToolbarGroupPickTitle(g));
        }
        ListPopupWindow lpw = new ListPopupWindow(this);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                labels);
        lpw.setAdapter(adapter);
        lpw.setAnchorView(groupIndicatorContainer);
        lpw.setModal(true);
        int minW = getResources().getDimensionPixelSize(R.dimen.toolbar_group_picker_min_width);
        lpw.setContentWidth(Math.max(groupIndicatorContainer.getWidth(), minW));
        int maxH = getResources().getDimensionPixelSize(R.dimen.toolbar_group_picker_max_height);
        int rowH = getResources().getDimensionPixelSize(R.dimen.list_row_min_height);
        int listPad = 2 * getResources().getDimensionPixelSize(R.dimen.grid_1);
        int estimatedFull = items.size() * rowH + listPad;
        lpw.setHeight(estimatedFull > maxH ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT);
        lpw.setOnItemClickListener((parent, view, position, id) -> {
            lpw.dismiss();
            if (position < 0 || position >= items.size()) {
                return;
            }
            chooseGroupFromToolbar(items.get(position).id);
        });
        lpw.setOnDismissListener(() -> toolbarGroupListPopup = null);
        toolbarGroupListPopup = lpw;
        lpw.show();
    }

    @NonNull
    private CharSequence buildToolbarGroupPickTitle(@NonNull GroupListItem g) {
        String name = g.name != null ? g.name.trim() : "";
        if (name.isEmpty()) {
            name = "—";
        }
        if (!isToolbarGroupListItemOwner(g)) {
            return name;
        }
        Drawable crown = ContextCompat.getDrawable(this, R.drawable.ic_crown);
        if (crown == null) {
            return name;
        }
        crown = DrawableCompat.wrap(crown.mutate());
        int tint = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(this, R.color.cs_primary));
        DrawableCompat.setTint(crown, tint);
        int iconPx = (int) (18 * getResources().getDisplayMetrics().scaledDensity + 0.5f);
        crown.setBounds(0, 0, iconPx, iconPx);
        SpannableString ss = new SpannableString(name + "\u00A0 ");
        ImageSpan span;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            span = new ImageSpan(crown, ImageSpan.ALIGN_CENTER);
        } else {
            span = new ImageSpan(crown);
        }
        ss.setSpan(span, name.length(), name.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    private static boolean isToolbarGroupListItemOwner(@NonNull GroupListItem g) {
        return g.role != null && "owner".equalsIgnoreCase(g.role.trim());
    }

    private void chooseGroupFromToolbar(@NonNull UUID groupId) {
        new Thread(() -> {
            GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
            Result<Void> r = repo.chooseGroup(groupId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (r instanceof Result.Success) {
                    popToRootTabIfNestedAfterToolbarGroupChange();
                    refreshCurrentGroup();
                    return;
                }
                if (r instanceof Result.HttpError) {
                    int code = ((Result.HttpError<Void>) r).httpCode;
                    if (code == 401) {
                        App.getDeps().tokenStorage.clear();
                        navigateToLoginClearingStack();
                        return;
                    }
                    if (code == 403 || code == 500) {
                        ErrorUi.show(this, findViewById(android.R.id.content), R.string.choose_group_error, ErrorUi.Duration.LONG);
                        return;
                    }
                }
                if (r instanceof Result.NetworkError) {
                    ErrorUi.show(this, findViewById(android.R.id.content), R.string.network_error, ErrorUi.Duration.SHORT);
                    return;
                }
                ErrorUi.show(this, findViewById(android.R.id.content), R.string.internal_error, ErrorUi.Duration.SHORT);
            });
        }).start();
    }

    private void navigateToLoginClearingStack() {
        if (navController == null) {
            return;
        }
        NavOptions opts = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build();
        navController.navigate(R.id.loginFragment, null, opts);
    }

    private void popToRootTabIfNestedAfterToolbarGroupChange() {
        if (navController == null || bottomNav == null) {
            return;
        }
        NavDestination dest = navController.getCurrentDestination();
        if (dest == null) {
            return;
        }
        int destId = dest.getId();
        if (isBottomNavRootScreen(destId)) {
            return;
        }
        int rootTabId = rootTabIdForNestedDestination(destId);
        if (rootTabId == 0) {
            return;
        }
        if (!navController.popBackStack(rootTabId, false)) {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(rootTabId, true)
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(rootTabId, null, opts);
        }
        bottomNav.setSelectedItemId(rootTabId);
    }

    private static boolean isBottomNavRootScreen(int destId) {
        return destId == R.id.groupsFragment
                || destId == R.id.coursesFragment
                || destId == R.id.calendarFragment
                || destId == R.id.newsFragment
                || destId == R.id.settingsFragment;
    }

    private static int rootTabIdForNestedDestination(int destId) {
        if (destId == R.id.createGroupFragment
                || destId == R.id.joinGroupFragment
                || destId == R.id.editGroupFragment
                || destId == R.id.groupMembersFragment) {
            return R.id.groupsFragment;
        }
        if (destId == R.id.createCourseFragment
                || destId == R.id.courseDetailFragment
                || destId == R.id.editCourseFragment
                || destId == R.id.courseSharedMaterialsFragment
                || destId == R.id.coursePersonalMaterialsFragment
                || destId == R.id.courseGradingFormulaFragment
                || destId == R.id.editCourseGradingFormulaFragment
                || destId == R.id.gradingElementScoresFragment) {
            return R.id.coursesFragment;
        }
        if (destId == R.id.createCalendarEventFragment
                || destId == R.id.calendarEventDetailFragment
                || destId == R.id.editCalendarEventFragment) {
            return R.id.calendarFragment;
        }
        if (destId == R.id.newsDetailFragment
                || destId == R.id.createNewsFragment) {
            return R.id.newsFragment;
        }
        return 0;
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

    private void syncCalendarEventEditButton(@Nullable Boolean allowed) {
        if (btnEditEvent == null || navController == null || navController.getCurrentDestination() == null) {
            return;
        }
        int destId = navController.getCurrentDestination().getId();
        if (destId != R.id.calendarEventDetailFragment) {
            btnEditEvent.setVisibility(View.GONE);
            return;
        }
        btnEditEvent.setVisibility(Boolean.TRUE.equals(allowed) ? View.VISIBLE : View.GONE);
    }

    private void syncCalendarEventEditButton() {
        syncCalendarEventEditButton(eventDetailToolbarVm.getEventEditAllowed().getValue());
    }

    public void refreshCalendarEventEditButton() {
        syncCalendarEventEditButton();
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
        if (destId == R.id.newsFragment) {
            Boolean owns = groupVm != null ? groupVm.getOwnerOfAnyGroup().getValue() : null;
            if (Boolean.TRUE.equals(owns)) {
                btnToolbarCreate.setVisibility(View.VISIBLE);
                btnToolbarCreate.setContentDescription(getString(R.string.add_news));
            } else {
                btnToolbarCreate.setVisibility(View.GONE);
            }
            return;
        }
        if (destId == R.id.calendarFragment) {
            btnToolbarCreate.setVisibility(View.VISIBLE);
            btnToolbarCreate.setContentDescription(getString(R.string.add_event));
            return;
        }
        if (onListTab && showOwnerToolbarActions(gs)) {
            btnToolbarCreate.setVisibility(View.VISIBLE);
            if (destId == R.id.coursesFragment) {
                btnToolbarCreate.setContentDescription(getString(R.string.create_course));
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

    public void updateNewsUnreadBadge(int count) {
        if (bottomNav == null) {
            return;
        }
        if (count <= 0) {
            bottomNav.removeBadge(R.id.newsFragment);
            return;
        }
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.newsFragment);
        badge.setVisible(true);
        badge.setBadgeGravity(BadgeDrawable.TOP_END);
        badge.setBackgroundColor(ContextCompat.getColor(this, R.color.cs_primary));
        badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.cs_on_primary));
        badge.clearNumber();
        badge.setText("+" + count);
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