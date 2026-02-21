package ru.katevpy.coursesync;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import ru.katevpy.coursesync.shared.SharedGroupViewModel;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private TextView tvGroupIndicator;
    private SharedGroupViewModel groupVm;

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

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        NavController navController = navHostFragment.getNavController();

        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        groupVm = new ViewModelProvider(this).get(SharedGroupViewModel.class);

        String savedGroup = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("selected_group_number", null);
        groupVm.setGroup(savedGroup);

        groupVm.getGroupState().observe(this, state -> {
            if (tvGroupIndicator == null) return;

            if (state != null && state.hasGroup()) {
                tvGroupIndicator.setText("Группа " + state.groupNumber);
            } else {
                tvGroupIndicator.setText("Нет группы");
            }
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();

            boolean isLogin = (id == R.id.loginFragment);

            bottomNav.setVisibility(isLogin ? View.GONE : View.VISIBLE);

            if (tvGroupIndicator != null) {
                tvGroupIndicator.setVisibility(isLogin ? View.GONE : View.VISIBLE);
            }
        });
    }

    public void setSelectedGroupAndPersist(@Nullable String groupNumber) {
        if (groupNumber == null || groupNumber.trim().isEmpty()) {
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .remove("selected_group_number")
                    .apply();
            groupVm.clearGroup();
        } else {
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("selected_group_number", groupNumber)
                    .apply();
            groupVm.setGroup(groupNumber);
        }
    }
}