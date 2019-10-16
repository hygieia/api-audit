package com.capitalone.dashboard.model;

public enum ScanState {
    COMPLETED("Completed"),
    INVALID("Invalid"),
    NO_FINDINGS("No Findings");


    private String state;

    ScanState(String state) {
        this.state = state;
    }

    public String getState() {
        return this.state;
    }

    @Override
    public String toString() {
        return this.state;
    }
}
