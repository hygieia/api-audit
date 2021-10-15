package com.capitalone.dashboard.util;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.stream.Collectors;

public class ConversionUtils {

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
}
