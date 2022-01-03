package com.capitalone.dashboard.util;

import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.GitComponent;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConversionUtils {

    public static final String TREE = "(.*\\/)(.*\\/)(.*\\/)(tree\\/)(.*)";

    public static final String flattenMap(Map<String, String[]> input) {
        return flattenMap(input, "", "");
    }

    public static final String flattenMap(Map<String, String[]> input, String prefix, String suffix) {
        if (MapUtils.isEmpty(input)) return "NONE";
        return input.keySet().stream()
                .map(key -> key + "=" + arrayToString(input.get(key)))
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    private static final String arrayToString(String[] input) {
        if (input == null || input.length == 0) return "";
        return String.join("-", input);
    }

    public static boolean matchAltIdentifier(CollectorItem collectorItem, String altIdentifier) {
        if (Objects.isNull(collectorItem.getAltIdentifier())) return false;
        if(Objects.isNull(altIdentifier)) return false;
        return getGitComponent(altIdentifier).equals(getGitComponent(collectorItem.getAltIdentifier()));
    }

    private static GitComponent getGitComponent(String altIdentifier) {
        try{
            Pattern pattern = Pattern.compile(TREE);
            Matcher matcher = pattern.matcher(altIdentifier);
            matcher.matches();
            String org = matcher.group(2).replace("/", "");
            String repo = matcher.group(3).replace("/", "");
            String branch = matcher.group(5).replace("/", "");
            return new GitComponent(org, repo, branch);
        }catch (IllegalStateException e){
            return new GitComponent("","","");
        }

    }
}
