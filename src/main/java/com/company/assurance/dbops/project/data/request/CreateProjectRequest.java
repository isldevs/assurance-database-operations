package com.company.assurance.dbops.project.data.request;

import com.company.assurance.dbops.project.entity.PromotionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String projectCode,
        @NotBlank @Size(max = 128) String projectName,
        @NotBlank @Size(max = 128) String ownerName,
        PromotionMode promotionMode) {

    public CreateProjectRequest(String projectCode, String projectName, String ownerName) {
        this(projectCode, projectName, ownerName, null);
    }
}
