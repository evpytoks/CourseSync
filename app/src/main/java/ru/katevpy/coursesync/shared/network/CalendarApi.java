package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
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

    @GET("calendar")
    Call<CalendarListResponse> listEvents(
            @Query("startDate") String startDate,
            @Query("endDate") String endDate
    );

    @GET("calendar/event-types")
    Call<CalendarEventTypeColorsResponse> getEventTypes();

    @POST("calendar/add")
    Call<Void> addEvent(@Body AddCalendarEventRequest request);

    @GET("calendar/{id}")
    Call<CalendarEventDetailsResponse> getEvent(@Path("id") UUID id);

    @PUT("calendar/{id}/change")
    Call<Void> changeEvent(@Path("id") UUID id, @Body UpdateCalendarEventRequest request);

    @DELETE("calendar/{id}/delete")
    Call<Void> deleteEvent(@Path("id") UUID id);

    @PUT("calendar/{id}/done")
    Call<Void> toggleEventDone(@Path("id") UUID id);
}
