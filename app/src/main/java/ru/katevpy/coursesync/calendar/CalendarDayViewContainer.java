package ru.katevpy.coursesync.calendar;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kizitonwose.calendar.view.ViewContainer;

import ru.katevpy.coursesync.R;

public class CalendarDayViewContainer extends ViewContainer {

    public final TextView dayText;
    public final LinearLayout eventDotsRow;

    public CalendarDayViewContainer(View view) {
        super(view);
        dayText = view.findViewById(R.id.calendarDayText);
        eventDotsRow = view.findViewById(R.id.eventDotsRow);
    }
}
