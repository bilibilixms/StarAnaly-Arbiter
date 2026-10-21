package com.staranaly.arbiter.task;

import java.text.Normalizer;
import java.util.Locale;

final class TaskTitleMatcher {

    private TaskTitleMatcher() {
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    static int maxDistance(String query) {
        int length = query.codePointCount(0, query.length());
        return length <= 2 ? 0 : length <= 5 ? 1 : 2;
    }

    static int distance(String title, String query) {
        int[] needle = query.codePoints().toArray();
        int[] haystack = normalize(title).codePoints().toArray();
        int allowed = maxDistance(query);
        if (needle.length == 0 || haystack.length == 0) {
            return allowed + 1;
        }
        if (needle.length > haystack.length + allowed) {
            return allowed + 1;
        }

        // The first row is zero so a match may start anywhere in the title.
        int[] previous = new int[haystack.length + 1];
        for (int i = 1; i <= needle.length; i++) {
            int[] current = new int[haystack.length + 1];
            current[0] = i;
            for (int j = 1; j <= haystack.length; j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (needle[i - 1] == haystack[j - 1] ? 0 : 1));
            }
            previous = current;
        }
        int best = Integer.MAX_VALUE;
        for (int value : previous) {
            best = Math.min(best, value);
        }
        return best <= allowed ? best : allowed + 1;
    }
}
