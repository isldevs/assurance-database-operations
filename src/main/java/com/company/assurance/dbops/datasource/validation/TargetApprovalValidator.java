package com.company.assurance.dbops.datasource.validation;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties;
import org.springframework.stereotype.Component;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties.ApprovedTarget;

import java.util.HashMap;
import java.util.Map;

@Component
public final class TargetApprovalValidator {
    private final Map<DataSourceKey, ApprovedTarget> approvedTargets;

    public TargetApprovalValidator(TargetApprovalProperties policy, DatabaseOperationsProperties operations) {
        Map<DataSourceKey, ApprovedTarget> targets = new HashMap<>();
        for (ApprovedTarget target : policy.approvedTargets()) {
            if (!target.nonProductionConfirmed()) {
                throw new IllegalStateException(
                        "Target approval requires non-production confirmation"
                );
            }

            ApprovedTarget existing = targets.putIfAbsent(target.key(), target);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate database target approval"
                );
            }
        }

        approvedTargets = Map.copyOf(targets);

        // Reject unapproved enabled registrations during startup.
        operations.registrations().stream()
                .filter(DataSourceRegistrationProperties::enabled)
                .forEach(this::assertApproved);
    }

    public void assertApproved(DataSourceRegistrationProperties registration) {
        ApprovedTarget approved = approvedTargets.get(registration.key());

        if (approved == null
                || approved.engine() != registration.engine()
                || !approved.jdbcUrl().equals(registration.jdbcUrl())) {
            throw new TargetNotApprovedException();
        }
    }
}
