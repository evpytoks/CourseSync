package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import ru.katevpy.coursesync.shared.dto.ChooseGroupResponse;
import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.GroupChangeRequest;
import ru.katevpy.coursesync.shared.dto.GroupChangeResponse;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.GroupJoinRequest;
import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;

public interface GroupApi {

    @GET("group/list")
    Call<GroupListResponse> listGroups();

    @GET("group/owner-list")
    Call<OwnerGroupListResponse> ownerListGroups();

    @GET("group/current")
    Call<GroupDetailsResponse> getCurrentGroup(@Query("_t") long timestamp);

    @POST("group/create")
    Call<CreateGroupResponse> createGroup(@Body CreateGroupRequest request);

    @POST("group/join")
    Call<GroupJoinResponse> joinGroup(@Body GroupJoinRequest request);

    @POST("group/{id}/choose")
    Call<ChooseGroupResponse> chooseGroup(@Path("id") java.util.UUID id);

    @PUT("group/{id}/change")
    Call<GroupChangeResponse> changeGroup(@Path("id") java.util.UUID id, @Body GroupChangeRequest request);
}