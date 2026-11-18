package com.company.assurance.dbops.project.response;

import com.company.assurance.dbops.environment.Environment;
import java.util.List;

public record ProjectEnvironmentResponse(
        Environment environment, boolean enabled, List<ProjectDataSourceResponse> dataSources) {}
