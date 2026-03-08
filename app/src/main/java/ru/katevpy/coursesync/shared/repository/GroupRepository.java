package ru.katevpy.coursesync.shared.repository;

import java.io.IOException;

import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.network.GroupApi;
import ru.katevpy.coursesync.shared.util.Result;

public class GroupRepository {

    private final GroupApi api;
    private final Gson gson = new Gson();

    public GroupRepository(GroupApi api) {
        this.api = api;
    }

    private ApiError parseError(ResponseBody body) {
        if (body == null) return null;
        try {
            ErrorEnvelope env = gson.fromJson(body.string(), ErrorEnvelope.class);
            return env != null ? env.error : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Result<GroupListResponse> getGroups() {
        try {
            Response<GroupListResponse> r = api.listGroups().execute();

            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }

            return Result.httpError(r.code(), null);

        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<CreateGroupResponse> createGroup(String name) {
        try {
            Response<CreateGroupResponse> r = api.createGroup(new CreateGroupRequest(name)).execute();

            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));

        } catch (IOException e) {
            return Result.networkError(e);
        }
    }
}