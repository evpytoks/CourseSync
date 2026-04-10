package ru.katevpy.coursesync.calendar;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import ru.katevpy.coursesync.ui.ErrorUi;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarEventDetailFragment extends Fragment {

    private TextView eventDetailName;
    private TextView eventDetailDate;
    private TextView eventDetailDescription;
    private MaterialCardView eventDetailDetailsCard;
    private MaterialButton btnDeleteEvent;
    private UUID eventId;
    @Nullable
    private UUID loadedEventGroupId;
    private EventDetailToolbarViewModel eventToolbarVm;
    private SharedGroupViewModel sharedGroupVm;

    public CalendarEventDetailFragment() {
        super(R.layout.fragment_calendar_event_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventDetailName = view.findViewById(R.id.eventDetailName);
        eventDetailDate = view.findViewById(R.id.eventDetailDate);
        eventDetailDescription = view.findViewById(R.id.eventDetailDescription);
        eventDetailDetailsCard = view.findViewById(R.id.eventDetailDetailsCard);
        btnDeleteEvent = view.findViewById(R.id.btnDeleteEvent);

        Bundle args = getArguments();
        if (args == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = args.getString("eventId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            eventId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        CalendarEventDetailViewModel viewModel = new ViewModelProvider(
                this,
                new CalendarEventDetailViewModelFactory()
        ).get(CalendarEventDetailViewModel.class);

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);

        eventToolbarVm = new ViewModelProvider(
                requireActivity(),
                new EventDetailToolbarViewModelFactory()
        ).get(EventDetailToolbarViewModel.class);

        btnDeleteEvent.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.event_delete_confirm)
                .setPositiveButton(R.string.event_yes, (dialog, which) -> eventToolbarVm.deleteEvent(eventId))
                .setNegativeButton(R.string.event_no, null)
                .show());

        sharedGroupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        sharedGroupVm.getGroupState().observe(getViewLifecycleOwner(),
                state -> syncOwnerActionsForEvent(state, sharedGroupVm.getOwnedGroupIds().getValue()));
        sharedGroupVm.getOwnedGroupIds().observe(getViewLifecycleOwner(),
                owned -> syncOwnerActionsForEvent(sharedGroupVm.getGroupState().getValue(), owned));
        syncOwnerActionsForEvent(sharedGroupVm.getGroupState().getValue(), sharedGroupVm.getOwnedGroupIds().getValue());
    }

    private void syncOwnerActionsForEvent(@Nullable GroupState state, @Nullable Set<String> ownedGroupIds) {
        if (btnDeleteEvent == null) return;
        boolean allow = canModifyEvent(state, loadedEventGroupId, ownedGroupIds);
        btnDeleteEvent.setVisibility(allow ? View.VISIBLE : View.GONE);
        if (eventToolbarVm != null) {
            eventToolbarVm.setEventEditAllowed(allow);
        }
        android.app.Activity a = getActivity();
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshCalendarEventEditButton();
        }
    }

    private static boolean canModifyEvent(
            @Nullable GroupState state,
            @Nullable UUID eventGroupId,
            @Nullable Set<String> ownedGroupIds) {
        if (eventGroupId == null) {
            return false;
        }
        String eventKey = eventGroupId.toString().toLowerCase(Locale.ROOT);
        if (ownedGroupIds != null && !ownedGroupIds.isEmpty() && ownedGroupIds.contains(eventKey)) {
            return true;
        }
        return state != null && state.isGroupOwner() && state.groupId != null
                && state.groupId.trim().toLowerCase(Locale.ROOT).equals(eventKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (eventId != null) {
            CalendarEventDetailViewModel vm = new ViewModelProvider(this, new CalendarEventDetailViewModelFactory()).get(CalendarEventDetailViewModel.class);
            vm.loadEvent(eventId);
        }
    }

    @Override
    public void onPause() {
        clearToolbarEventDetail();
        super.onPause();
    }

    private void applyToolbarEventDetailMeta(@Nullable String metaLine) {
        TextView tv = requireActivity().findViewById(R.id.tvToolbarNewsDetailMeta);
        if (tv == null) {
            return;
        }
        if (metaLine == null || metaLine.isEmpty()) {
            tv.setVisibility(View.GONE);
            tv.setText("");
            tv.setContentDescription(null);
            return;
        }
        tv.setText(metaLine);
        tv.setVisibility(View.VISIBLE);
        tv.setContentDescription(metaLine);
    }

    private void clearToolbarEventDetail() {
        android.app.Activity a = getActivity();
        if (a == null) {
            return;
        }
        TextView timeTv = a.findViewById(R.id.tvToolbarNewsDetailTime);
        if (timeTv != null) {
            timeTv.setVisibility(View.GONE);
            timeTv.setText("");
            timeTv.setContentDescription(null);
        }
        TextView metaTv = a.findViewById(R.id.tvToolbarNewsDetailMeta);
        if (metaTv != null) {
            metaTv.setVisibility(View.GONE);
            metaTv.setText("");
            metaTv.setContentDescription(null);
        }
    }

    @Nullable
    private static String trimmedOrNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String formatToolbarMetaLine(@NonNull CalendarEventDetailsResponse d) {
        StringBuilder sb = new StringBuilder();
        appendToolbarMetaPart(sb, trimmedOrNull(d.groupName));
        appendToolbarMetaPart(sb, trimmedOrNull(d.courseName));
        appendToolbarMetaPart(sb, trimmedOrNull(d.eventType));
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static void appendToolbarMetaPart(@NonNull StringBuilder sb, @Nullable String part) {
        if (part == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(part);
    }

    private void bindEventContent(@NonNull CalendarEventDetailsResponse d) {
        String name = trimmedOrNull(d.name);
        if (name != null) {
            eventDetailName.setText(name);
            eventDetailName.setVisibility(View.VISIBLE);
        } else {
            eventDetailName.setText("");
            eventDetailName.setVisibility(View.GONE);
        }

        String dateLine = formatDate(d.date);
        if (!dateLine.isEmpty()) {
            eventDetailDate.setText(dateLine);
            eventDetailDate.setVisibility(View.VISIBLE);
        } else {
            eventDetailDate.setText("");
            eventDetailDate.setVisibility(View.GONE);
        }

        String desc = trimmedOrNull(d.description);

        if (desc != null) {
            eventDetailDescription.setText(desc);
            eventDetailDescription.setVisibility(View.VISIBLE);
        } else {
            eventDetailDescription.setText("");
            eventDetailDescription.setVisibility(View.GONE);
        }

        eventDetailDetailsCard.setVisibility(desc != null ? View.VISIBLE : View.GONE);
    }

    private void onLoadResult(@Nullable Result<CalendarEventDetailsResponse> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            CalendarEventDetailsResponse data = ((Result.Success<CalendarEventDetailsResponse>) result).data;
            loadedEventGroupId = data.groupId;
            if (sharedGroupVm != null) {
                syncOwnerActionsForEvent(
                        sharedGroupVm.getGroupState().getValue(),
                        sharedGroupVm.getOwnedGroupIds().getValue());
            }
            if (isAdded()) {
                applyToolbarEventDetailMeta(formatToolbarMetaLine(data));
            }
            bindEventContent(data);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CalendarEventDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 404 || code == 403) {
                loadedEventGroupId = null;
                if (eventToolbarVm != null) {
                    eventToolbarVm.setEventEditAllowed(false);
                }
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.calendar_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        loadedEventGroupId = null;
        if (eventToolbarVm != null) {
            eventToolbarVm.setEventEditAllowed(false);
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        String core = dateStr;
        if (core.endsWith("Z")) {
            core = core.substring(0, core.length() - 1);
        }
        int tzPlus = core.indexOf('+', 10);
        if (tzPlus > 0) {
            core = core.substring(0, tzPlus);
        }
        try {
            if (core.length() >= 10) {
                LocalDate date = LocalDate.parse(core.substring(0, 10));
                String formatted = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru")));
                if (core.length() > 10) {
                    String afterT = core.length() > 11 ? core.substring(11) : "";
                    if (!afterT.isEmpty() && !afterT.startsWith("00:00")) {
                        String timePart = afterT.length() >= 5 ? afterT.substring(0, 5) : afterT;
                        return formatted + " " + timePart;
                    }
                }
                return formatted;
            }
        } catch (DateTimeParseException ignored) {
        }
        return dateStr;
    }
}
