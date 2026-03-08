package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GroupListResponse {
    @SerializedName(value = "Groups", alternate = {"groups", "items"})
    public List<GroupListItem> items;
}