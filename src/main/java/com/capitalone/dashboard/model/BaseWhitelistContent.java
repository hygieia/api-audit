package com.capitalone.dashboard.model;

import java.util.List;
import java.util.Map;

public abstract class BaseWhitelistContent {

    private Map<String,String> contentPatterns;

    public BaseWhitelistContent(Map<String,String> contentPatterns) {
        this.contentPatterns = contentPatterns;
    };

    abstract boolean pluginDidThis(String commitContent);

    public abstract boolean isWhitelistedCommitContent(List<RepoFile> commitFiles);

    public Map<String, String> getContentPatterns() { return contentPatterns; }

    public String getContentPattern(String patternType) { return (contentPatterns != null) ? contentPatterns.get(patternType) : null; }

    public void setContentPatterns(Map<String, String> contentPatterns) { this.contentPatterns = contentPatterns; }
}
