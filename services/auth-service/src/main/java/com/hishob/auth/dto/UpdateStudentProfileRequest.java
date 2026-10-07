package com.hishob.auth.dto;

import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class UpdateStudentProfileRequest {

    @Size(max = 150, message = "Department cannot exceed 150 characters")
    private String department;

    @Size(max = 50, message = "Year cannot exceed 50 characters")
    private String year;

    @Size(max = 50, message = "Cannot have more than 50 skills")
    private List<@Size(max = 100, message = "Each skill cannot exceed 100 characters") String> skills = new ArrayList<>();

    @Size(max = 50, message = "Cannot have more than 50 interests")
    private List<@Size(max = 100, message = "Each interest cannot exceed 100 characters") String> interests = new ArrayList<>();

    public UpdateStudentProfileRequest() {
    }

    public UpdateStudentProfileRequest(String department,
                                       String year,
                                       List<String> skills,
                                       List<String> interests) {
        this.department = department;
        this.year = year;
        this.skills = skills != null ? skills : new ArrayList<>();
        this.interests = interests != null ? interests : new ArrayList<>();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }
}
