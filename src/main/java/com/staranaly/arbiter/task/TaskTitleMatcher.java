package com.staranaly.arbiter.task;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import net.sourceforge.pinyin4j.PinyinHelper;

final class TaskTitleMatcher {

    private static final ConcurrentMap<Integer, Set<String>> PINYIN_CACHE = new ConcurrentHashMap<>();

    private TaskTitleMatcher() {
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    static int maxDistance(String query) {
        int length = query.codePointCount(0, query.length());
        return length + allowedTypos(length) * (length + 1);
    }

    static int distance(String title, String query) {
        int[] needle = query.codePoints().toArray();
        String normalizedTitle = normalize(title);
        if (!query.isEmpty() && normalizedTitle.contains(query)) {
            return 0;
        }
        int[] haystack = normalizedTitle.codePoints().toArray();
        int allowed = maxDistance(query);
        if (needle.length == 0 || haystack.length == 0) {
            return allowed + 1;
        }
        if (needle.length > haystack.length + allowedTypos(needle.length)) {
            return allowed + 1;
        }

        int editCost = needle.length + 1;
        List<Set<String>> needlePinyin = Arrays.stream(needle).mapToObj(TaskTitleMatcher::readings).toList();
        List<Set<String>> titlePinyin = Arrays.stream(haystack).mapToObj(TaskTitleMatcher::readings).toList();

        // The first row is zero so a match may start anywhere in the title.
        int[] previous = new int[haystack.length + 1];
        for (int i = 1; i <= needle.length; i++) {
            int[] current = new int[haystack.length + 1];
            current[0] = i * editCost;
            for (int j = 1; j <= haystack.length; j++) {
                int substitution = needle[i - 1] == haystack[j - 1] ? 0
                        : sharesPinyin(needlePinyin.get(i - 1), titlePinyin.get(j - 1)) ? 1 : editCost;
                current[j] = Math.min(Math.min(previous[j] + editCost, current[j - 1] + editCost),
                        previous[j - 1] + substitution);
            }
            previous = current;
        }
        int best = Integer.MAX_VALUE;
        for (int value : previous) {
            best = Math.min(best, value);
        }
        return best <= allowed ? best : allowed + 1;
    }

    private static int allowedTypos(int length) {
        return length <= 2 ? 0 : length <= 5 ? 1 : 2;
    }

    private static boolean sharesPinyin(Set<String> first, Set<String> second) {
        for (String syllable : first) {
            if (second.contains(syllable)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> readings(int codePoint) {
        if (codePoint > Character.MAX_VALUE
                || Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) {
            return Set.of();
        }
        return PINYIN_CACHE.computeIfAbsent(codePoint, value -> {
            String[] variants = PinyinHelper.toHanyuPinyinStringArray((char) value.intValue());
            if (variants == null) {
                return Set.of();
            }
            return Arrays.stream(variants)
                    .filter(syllable -> syllable != null && !syllable.startsWith("none"))
                    .map(syllable -> syllable.replaceFirst("[0-5]$", ""))
                    .collect(Collectors.toUnmodifiableSet());
        });
    }
}
