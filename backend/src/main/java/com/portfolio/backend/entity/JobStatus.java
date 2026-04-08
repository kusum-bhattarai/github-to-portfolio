package com.portfolio.backend.entity;

public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == QUEUED || this == PROCESSING || this == RETRYING;
    }
}
