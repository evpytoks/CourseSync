package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;

public interface GroupApi {

    @GET("group/list")
    Call<GroupListResponse> listGroups();

    @POST("group/create")
    Call<CreateGroupResponse> createGroup(@Body CreateGroupRequest request);
}