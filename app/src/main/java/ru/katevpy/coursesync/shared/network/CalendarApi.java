package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.UUID;

import ru.katevpy.coursesync.shared.dto.AddCalendarEventRequest;
import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorsResponse;
import ru.katevpy.coursesync.shared.dto.CalendarListResponse;
import ru.katevpy.coursesync.shared.dto.UpdateCalendarEventRequest;

public interface CalendarApi {

    @GET("calendar-events")
    Call<CalendarListResponse> listEvents(
            @Query("startDate") String startDate,
            @Query("endDate") String endDate
    );

    @GET("calendar-events/types")
    Call<CalendarEventTypeColorsResponse> getEventTypes();

    @POST("calendar-events")
    Call<Void> addEvent(@Body AddCalendarEventRequest request);

    @GET("calendar-events/{id}")
    Call<CalendarEventDetailsResponse> getEvent(@Path("id") UUID id);

    @PUT("calendar-events/{id}")
    Call<Void> changeEvent(@Path("id") UUID id, @Body UpdateCalendarEventRequest request);

    @DELETE("calendar-events/{id}")
    Call<Void> deleteEvent(@Path("id") UUID id);

    @PATCH("calendar-events/{id}")
    Call<Void> toggleEventDone(@Path("id") UUID id);
}
