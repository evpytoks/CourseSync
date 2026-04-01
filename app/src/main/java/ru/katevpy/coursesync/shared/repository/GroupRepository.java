package ru.katevpy.coursesync.shared.repository;

import java.util.UUID;

import java.io.EOFException;
import java.io.IOException;

import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.ChooseGroupResponse;
import ru.katevpy.coursesync.shared.dto.CreateGroupRequest;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import ru.katevpy.coursesync.shared.dto.GroupChangeRequest;
import ru.katevpy.coursesync.shared.dto.GroupChangeResponse;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.GroupJoinRequest;
import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
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
            if (r.isSuccessful()) {
                GroupListResponse body = r.body();
                if (body != null) {
                    return Result.success(body);
                }
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        } catch (RuntimeException e) {
            return Result.logicalError("Ошибка разбора ответа");
        }
    }

    public Result<OwnerGroupListResponse> getOwnerGroups() {
        try {
            Response<OwnerGroupListResponse> r = api.ownerListGroups().execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
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
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<GroupJoinResponse> joinGroup(String code) {
        try {
            Response<GroupJoinResponse> r = api.joinGroup(new GroupJoinRequest(code)).execute();

            if (r.isSuccessful()) {
                return Result.success(r.body());
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));

        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<GroupDetailsResponse> getCurrentGroup() {
        try {
            Response<GroupDetailsResponse> r = api.getCurrentGroup(System.currentTimeMillis()).execute();
            GroupDetailsResponse body = r.body();
            if (r.isSuccessful() && body != null) {
                return Result.success(body);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<ChooseGroupResponse> chooseGroup(UUID groupId) {
        try {
            Response<ChooseGroupResponse> r = api.chooseGroup(groupId).execute();

            if (r.isSuccessful()) {
                return Result.success(r.body());
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));

        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        } catch (RuntimeException e) {
            return Result.logicalError("Ошибка разбора ответа");
        }
    }

    public Result<GroupChangeResponse> changeGroupName(java.util.UUID groupId, String name) {
        try {
            Response<GroupChangeResponse> r = api.changeGroup(groupId, new GroupChangeRequest(name)).execute();

            if (r.isSuccessful()) {
                return Result.success(r.body());
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));

        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<Void> deleteGroup(UUID groupId) {
        try {
            Response<Void> r = api.deleteGroup(groupId).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        } catch (RuntimeException e) {
            return Result.logicalError("Ошибка разбора ответа");
        }
    }
}