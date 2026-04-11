package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.GroupChangeRequest;
import ru.katevpy.coursesync.shared.dto.GroupChangeResponse;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.GroupJoinRequest;
import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
import ru.katevpy.coursesync.shared.dto.SetCurrentGroupRequest;

public interface GroupApi {

    @GET("groups")
    Call<GroupListResponse> listGroups();

    @GET("groups/owned")
    Call<OwnerGroupListResponse> ownerListGroups();

    @GET("me/current-group")
    Call<GroupDetailsResponse> getCurrentGroup(@Query("_t") long timestamp);

    @POST("groups")
    Call<CreateGroupResponse> createGroup(@Body CreateGroupRequest request);

    @POST("groups/join")
    Call<GroupJoinResponse> joinGroup(@Body GroupJoinRequest request);

    @PUT("me/current-group")
    Call<Void> setCurrentGroup(@Body SetCurrentGroupRequest request);

    @DELETE("groups/{id}/members/me")
    Call<Void> leaveGroup(@Path("id") java.util.UUID id);

    @PUT("groups/{id}")
    Call<GroupChangeResponse> changeGroup(@Path("id") java.util.UUID id, @Body GroupChangeRequest request);

    @DELETE("groups/{id}")
    Call<Void> deleteGroup(@Path("id") java.util.UUID id);
}
