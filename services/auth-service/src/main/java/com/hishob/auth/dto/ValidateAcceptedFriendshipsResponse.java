package com.hishob.auth.dto;

import java.util.List;
import java.util.UUID;

public class ValidateAcceptedFriendshipsResponse {

    private boolean valid;
    private List<UUID> missingUserIds;
    private List<UUID> nonAcceptedFriendIds;

    public ValidateAcceptedFriendshipsResponse() {
    }

    public ValidateAcceptedFriendshipsResponse(
            boolean valid,
            List<UUID> missingUserIds,
            List<UUID> nonAcceptedFriendIds) {
        this.valid = valid;
        this.missingUserIds = missingUserIds;
        this.nonAcceptedFriendIds = nonAcceptedFriendIds;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<UUID> getMissingUserIds() {
        return missingUserIds;
    }

    public void setMissingUserIds(List<UUID> missingUserIds) {
        this.missingUserIds = missingUserIds;
    }

    public List<UUID> getNonAcceptedFriendIds() {
        return nonAcceptedFriendIds;
    }

    public void setNonAcceptedFriendIds(List<UUID> nonAcceptedFriendIds) {
        this.nonAcceptedFriendIds = nonAcceptedFriendIds;
    }
}
