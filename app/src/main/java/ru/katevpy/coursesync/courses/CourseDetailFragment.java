package ru.katevpy.coursesync.courses;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.katevpy.coursesync.ui.ErrorUi;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.calendar.CalendarEventListBinder;
import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseContactMethodItem;
import ru.katevpy.coursesync.shared.dto.CourseContactPersonItem;
import ru.katevpy.coursesync.shared.dto.CalendarListItem;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseDetailFragment extends Fragment {

    private TextView courseDetailName;
    private TextView courseDetailGeneral;
    private LinearLayout courseDetailLinksList;
    private LinearLayout courseDetailContactsList;
    private View courseDetailLinksCard;
    private View courseDetailContactsCard;
    private View courseDetailEventsCard;
    private LinearLayout courseDetailEventsList;
    private UUID courseUuid;
    private String courseIdStr;
    private String loadedCourseName;
    private CourseDetailViewModel viewModel;

    public CourseDetailFragment() {
        super(R.layout.fragment_course_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        courseDetailName = view.findViewById(R.id.courseDetailName);
        courseDetailGeneral = view.findViewById(R.id.courseDetailGeneral);
        courseDetailLinksList = view.findViewById(R.id.courseDetailLinksList);
        courseDetailContactsList = view.findViewById(R.id.courseDetailContactsList);
        courseDetailLinksCard = view.findViewById(R.id.courseDetailLinksCard);
        courseDetailContactsCard = view.findViewById(R.id.courseDetailContactsCard);
        courseDetailEventsCard = view.findViewById(R.id.courseDetailEventsCard);
        courseDetailEventsList = view.findViewById(R.id.courseDetailEventsList);

        if (getArguments() == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = getArguments().getString("courseId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            courseUuid = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        courseIdStr = idStr;

        viewModel = new ViewModelProvider(this, new CourseDetailViewModelFactory())
                .get(CourseDetailViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getDeleteCourseResult().observe(getViewLifecycleOwner(), this::onDeleteCourseResult);
        viewModel.getCourseCalendarResult().observe(getViewLifecycleOwner(), this::onCourseCalendarResult);
        viewModel.getToggleCalendarFailure().observe(getViewLifecycleOwner(), this::onToggleCalendarFailure);

        view.findViewById(R.id.courseMaterialsShared).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_courseSharedMaterialsFragment, args);
        });
        view.findViewById(R.id.courseMaterialsPersonal).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_coursePersonalMaterialsFragment, args);
        });
        view.findViewById(R.id.courseGradingFormula).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_courseGradingFormulaFragment, args);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        attachCourseToolbarMenu();
        if (courseUuid != null) {
            viewModel.loadCourse(courseUuid);
            viewModel.loadCourseCalendar(courseUuid);
        }
    }

    @Override
    public void onPause() {
        detachCourseToolbarMenu();
        super.onPause();
    }

    private void attachCourseToolbarMenu() {
        if (courseUuid == null) {
            return;
        }
        View btn = requireActivity().findViewById(R.id.btnEditCourse);
        if (btn != null) {
            btn.setOnClickListener(this::onCourseToolbarEditClick);
        }
    }

    private void detachCourseToolbarMenu() {
        View btn = requireActivity().findViewById(R.id.btnEditCourse);
        if (btn != null) {
            btn.setOnClickListener(null);
        }
    }

    private void onCourseToolbarEditClick(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.course_owner_actions, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_owner_edit_course) {
                Bundle args = new Bundle();
                args.putString("courseId", courseIdStr);
                NavHostFragment.findNavController(CourseDetailFragment.this)
                        .navigate(R.id.action_courseDetailFragment_to_editCourseFragment, args);
                return true;
            }
            if (itemId == R.id.action_owner_delete_course) {
                showDeleteCourseDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showDeleteCourseDialog() {
        if (courseUuid == null) {
            return;
        }
        String raw = loadedCourseName;
        String display = (raw != null && !raw.trim().isEmpty())
                ? raw.trim()
                : getString(R.string.delete_course_unnamed_placeholder);
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.delete_course_dialog_message, display))
                .setPositiveButton(R.string.event_yes, (dialog, which) -> viewModel.deleteCourse(courseUuid))
                .setNegativeButton(R.string.event_no, null)
                .show();
    }

    private void onCourseCalendarResult(@Nullable Result<List<CalendarListItem>> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            List<CalendarListItem> list = ((Result.Success<List<CalendarListItem>>) result).data;
            if (list == null) {
                list = Collections.emptyList();
            }
            courseDetailEventsCard.setVisibility(View.VISIBLE);
            CalendarEventListBinder.bind(
                    courseDetailEventsList,
                    list,
                    this,
                    id -> viewModel.toggleCourseCalendarEventDone(id),
                    R.id.action_courseDetailFragment_to_calendarEventDetailFragment);
            return;
        }
        courseDetailEventsCard.setVisibility(View.GONE);
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CalendarListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 400) {
                ErrorUi.show(this, R.string.calendar_no_group, ErrorUi.Duration.SHORT);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.calendar_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        } else if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
        }
    }

    private void onToggleCalendarFailure(@Nullable Result<Void> r) {
        if (r == null) {
            return;
        }
        if (r instanceof Result.HttpError) {
            int code = ((Result.HttpError<?>) r).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                viewModel.consumeToggleCalendarFailure();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.calendar_load_error, ErrorUi.Duration.SHORT);
            } else {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
            }
        } else if (r instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
        }
        viewModel.consumeToggleCalendarFailure();
    }

    private void onDeleteCourseResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403 || code == 404 || code == 500) {
                ErrorUi.show(this, R.string.delete_course_server_error, ErrorUi.Duration.LONG);
                return;
            }
            if (code == 400) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void onLoadResult(@Nullable Result<CourseDetailsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseDetailsResponse data = ((Result.Success<CourseDetailsResponse>) result).data;
            if (data == null) return;
            loadedCourseName = data.name;
            courseDetailName.setText(data.name != null ? data.name : "");
            courseDetailGeneral.setText(data.generalInfo != null ? data.generalInfo : "");
            bindContacts(data.contacts);
            bindUsefulLinks(data.usefulLinks);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403 || code == 404) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 400) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.course_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void bindUsefulLinks(@Nullable List<CourseUsefulLinkItem> links) {
        courseDetailLinksList.removeAllViews();
        if (links == null || links.isEmpty()) {
            courseDetailLinksCard.setVisibility(View.GONE);
            return;
        }
        courseDetailLinksCard.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (CourseUsefulLinkItem item : links) {
            if (item == null) {
                continue;
            }
            String url = item.url != null ? item.url.trim() : "";
            if (url.isEmpty()) {
                continue;
            }
            View row = inflater.inflate(R.layout.item_course_useful_link, courseDetailLinksList, false);
            TextView titleView = row.findViewById(R.id.courseUsefulLinkTitle);
            String title = item.title != null ? item.title.trim() : "";
            if (title.isEmpty()) {
                title = getString(R.string.link_default_title);
            }
            titleView.setText(title);
            row.setContentDescription(getString(R.string.course_useful_link_row_a11y, title));
            row.setOnClickListener(v -> openExternalUrl(url));
            courseDetailLinksList.addView(row);
        }
        if (courseDetailLinksList.getChildCount() == 0) {
            courseDetailLinksCard.setVisibility(View.GONE);
        }
    }

    private void bindContacts(@Nullable List<CourseContactPersonItem> contacts) {
        courseDetailContactsList.removeAllViews();
        if (contacts == null || contacts.isEmpty()) {
            courseDetailContactsCard.setVisibility(View.GONE);
            return;
        }
        courseDetailContactsCard.setVisibility(View.VISIBLE);
        int rowGap = getResources().getDimensionPixelSize(R.dimen.grid_1);
        int valueTypeGap = getResources().getDimensionPixelSize(R.dimen.grid_2);
        int valueMaxW = getResources().getDimensionPixelSize(R.dimen.contact_method_value_max_width);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (CourseContactPersonItem person : contacts) {
            if (person == null) {
                continue;
            }
            String personName = person.name != null ? person.name.trim() : "";
            if (personName.isEmpty()) {
                continue;
            }
            List<CourseContactMethodItem> methods = person.contactMethods != null
                    ? person.contactMethods
                    : Collections.emptyList();

            View personCard = inflater.inflate(R.layout.item_course_contact_person_detail, courseDetailContactsList, false);
            TextView nameView = personCard.findViewById(R.id.courseDetailContactPersonName);
            LinearLayout methodsContainer = personCard.findViewById(R.id.courseDetailContactPersonMethods);
            nameView.setText(personName);

            boolean addedAny = false;
            int methodIndex = 0;
            for (CourseContactMethodItem method : methods) {
                if (method == null) {
                    continue;
                }
                String value = method.value != null ? method.value.trim() : "";
                if (value.isEmpty()) {
                    continue;
                }
                String type = method.type != null ? method.type.trim() : "";
                addedAny = true;

                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (methodIndex > 0) {
                    rowLp.topMargin = rowGap;
                }
                methodIndex++;
                row.setLayoutParams(rowLp);

                TextView valueView = new TextView(requireContext());
                TextViewCompat.setTextAppearance(valueView, R.style.TextAppearance_CourseSync_ContactValue);
                valueView.setText(value);
                valueView.setMaxLines(1);
                valueView.setEllipsize(TextUtils.TruncateAt.END);
                valueView.setBackgroundResource(R.drawable.bg_contact_value_tonal_ripple);
                int hChip = getResources().getDimensionPixelSize(R.dimen.grid_2);
                int vChip = getResources().getDimensionPixelSize(R.dimen.grid_1);
                valueView.setPadding(hChip, vChip, hChip, vChip);
                valueView.setClickable(true);
                valueView.setFocusable(true);
                valueView.setOnClickListener(v -> copyToClipboard(value));
                valueView.setMaxWidth(valueMaxW);
                LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (!type.isEmpty()) {
                    valueLp.setMarginEnd(valueTypeGap);
                }
                valueView.setLayoutParams(valueLp);
                row.addView(valueView);

                if (!type.isEmpty()) {
                    TextView typeView = new TextView(requireContext());
                    TextViewCompat.setTextAppearance(typeView, R.style.TextAppearance_CourseSync_ContactTypeCaption);
                    typeView.setText(type);
                    typeView.setMaxLines(2);
                    typeView.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f);
                    typeView.setLayoutParams(typeLp);
                    typeView.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    typeView.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
                    row.addView(typeView);
                }

                methodsContainer.addView(row);
            }
            methodsContainer.setVisibility(addedAny ? View.VISIBLE : View.GONE);
            courseDetailContactsList.addView(personCard);
        }
        if (courseDetailContactsList.getChildCount() == 0) {
            courseDetailContactsCard.setVisibility(View.GONE);
        }
    }

    private void copyToClipboard(@NonNull String value) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("contact_value", value));
        ErrorUi.show(this, R.string.contact_value_copied, ErrorUi.Duration.SHORT);
    }

    private void openExternalUrl(@NonNull String raw) {
        String u = raw.trim();
        if (u.isEmpty()) {
            return;
        }
        if (!u.matches("(?i)https?://.*")) {
            u = "https://" + u;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            ErrorUi.show(this, R.string.link_open_error, ErrorUi.Duration.SHORT);
        }
    }
}
