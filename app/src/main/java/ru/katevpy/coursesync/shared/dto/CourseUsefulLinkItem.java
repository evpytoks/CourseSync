package ru.katevpy.coursesync.shared.dto;

public final class CourseUsefulLinkItem {

    public String title;
    public String url;

    public CourseUsefulLinkItem() {
    }

    public CourseUsefulLinkItem(String title, String url) {
        this.title = title;
        this.url = url;
    }
}
