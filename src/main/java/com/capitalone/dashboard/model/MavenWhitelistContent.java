package com.capitalone.dashboard.model;

import org.apache.commons.lang3.StringUtils;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenWhitelistContent extends BaseWhitelistContent {

    private static final String SNAPSHOT_TAG_PATTERN = "snapshotTagPattern";
    private static final String RELEASE_TAG_PATTERN = "releaseTagPattern";
    private static final String INDENT_PATTERN = "indentPattern";
    private static final String TAG_TAG_PATTERN = "tagTagPattern";

    public MavenWhitelistContent(WhitelistCommitType mavenWhitelistCommitType) {
        super(mavenWhitelistCommitType.getContentPatterns());
    }

    /* Example valid patch:
    "@@ -2,7 +2,7 @@\n
       <artifactId>some-artifact</artifactId>\n
    -  <version>1.0.0</version>\n
    +  <version>1.0.1-SNAPSHOT</version>\n
       <scm>\n
     -    <tag>some-old-tag</tag>\n
     +    <tag>some-new-tag</tag>\n
       </scm>"
     */

    @Override
    boolean pluginDidThis(String patch) {
        if (StringUtils.isEmpty(patch) ) return true;
        String[] patchArray = StringUtils.split(patch,"\n");
        int idx = 0;
        while (idx < patchArray.length) {
            // check if any required patterns are not provided or empty, bypass check and just return true
            if (StringUtils.isEmpty(getContentPattern(SNAPSHOT_TAG_PATTERN))
                    || StringUtils.isEmpty(getContentPattern(RELEASE_TAG_PATTERN))
                    || StringUtils.isEmpty(getContentPattern(INDENT_PATTERN))
                    || StringUtils.isEmpty(getContentPattern(TAG_TAG_PATTERN))) return true;

            // skip context lines until we reach changes
            boolean isIndentedLine = patchArray[idx].startsWith(" ") && patternMatcher(getContentPattern(INDENT_PATTERN)).matcher(patchArray[idx]).matches();
            if (patchArray[idx].startsWith("@@") || isIndentedLine) {
                idx += 1;
                continue;
            }

            // Expectation: Line pairs where exactly one line was deleted and replaced
            if (!patchArray[idx].startsWith("- ")) return false;
            if (!patchArray[idx].startsWith("- ") || !patchArray[idx+1].startsWith("+ ")) return false;

            // Legitimate Pattern #1: Remove "-SNAPSHOT" on a version element
            Matcher isRemoveSnapshot = patternMatcher(getContentPattern(SNAPSHOT_TAG_PATTERN)).matcher(patchArray[idx]);
            if (isRemoveSnapshot.matches()) {
                String expectedStringPattern = "^.\\W+(" + isRemoveSnapshot.group(1) + isRemoveSnapshot.group(2) + ".*)";
                boolean isExpectedString = patternMatcher(expectedStringPattern).matcher(patchArray[idx+1]).matches();
                if (!isExpectedString) {
                    return false;
                }

                idx += 2;
                continue;
            }

            // Legitimate Pattern #2: Change number on version element and add "-SNAPSHOT" back
            boolean isReleaseTag = patternMatcher(getContentPattern(RELEASE_TAG_PATTERN)).matcher(patchArray[idx]).matches();
            boolean isSnapshot = patternMatcher(getContentPattern(SNAPSHOT_TAG_PATTERN)).matcher(patchArray[idx+1]).matches();
            if (isReleaseTag && isSnapshot) {
                idx += 2;
                continue;
            }

            // Legitimate Pattern #3: Change numbers on tag element
            boolean isRemoveOldTag = patternMatcher(getContentPattern(TAG_TAG_PATTERN)).matcher(patchArray[idx]).matches();
            boolean isAddNewTag = patternMatcher(getContentPattern(TAG_TAG_PATTERN)).matcher(patchArray[idx+1]).matches();
            if (!isRemoveOldTag || !isAddNewTag) return false;

            idx += 2;
        }

        // Nothing is wrong with patch so plugin made this change
        return true;
    }

    @Override
    public boolean isWhitelistedCommitContent(List<RepoFile> commitFiles) {
        for (RepoFile file : commitFiles) {
            if (file.getFilename().endsWith("package.json") || file.getFilename().endsWith("package-lock.json")) continue;

            if (!file.getFilename().endsWith("pom.xml")) return false;

            if (!pluginDidThis(file.getPatch())) return false;
        }
        return true;
    }

    public Pattern patternMatcher(String pattern) { return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE); }

}