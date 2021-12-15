package com.capitalone.dashboard.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class GitComponent {

    private String org;
    private String repo;
    private String branch;

    public GitComponent(String org, String repo, String branch) {
        this.org = org;
        this.repo = repo;
        this.branch = branch;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GitComponent)) return false;
        GitComponent that = (GitComponent) o;
        return StringUtils.equalsIgnoreCase(getOrg(), that.getOrg()) &&
                StringUtils.equalsIgnoreCase(getRepo(), that.getRepo()) &&
                StringUtils.equalsIgnoreCase(getBranch(), that.getBranch());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOrg(), getRepo(), getBranch());
    }
}
