package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

import ru.katevpy.coursesync.shared.dto.ChooseGroupResponse;
import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.GroupJoinRequest;
import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;

public interface GroupApi {

    @GET("group/list")
    Call<GroupListResponse> listGroups();

    @POST("group/create")
    Call<CreateGroupResponse> createGroup(@Body CreateGroupRequest request);

    @POST("group/join")
    Call<GroupJoinResponse> joinGroup(@Body GroupJoinRequest request);

    @POST("group/{id}/choose")
    Call<ChooseGroupResponse> chooseGroup(@Path("id") java.util.UUID id);
}