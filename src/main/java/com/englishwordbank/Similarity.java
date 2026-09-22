package com.englishwordbank;

/** A transparent, demo-scale spelling similarity score. */
public final class Similarity {
    private Similarity() { }

    public static double score(String left, String right) {
        String a = left.toLowerCase().replaceAll("[^a-z]", "");
        String b = right.toLowerCase().replaceAll("[^a-z]", "");
        if (a.isEmpty() || b.isEmpty()) return 0;
        double edit = 1.0 - ((double) levenshtein(a, b) / Math.max(a.length(), b.length()));
        int shorterLength = Math.min(a.length(), b.length());
        double prefix = commonPrefix(a, b) / (double) shorterLength;
        double suffix = commonSuffix(a, b) / (double) shorterLength;
        double longestBlock = longestCommonSubstring(a, b) / (double) Math.max(a.length(), b.length());
        // People often recognize a word by its outer outline first.  Combining
        // start and end matching makes derive/deprive a useful "easy to confuse"
        // pair even though one word has an inserted internal letter.
        double outline = Math.min(1.0, prefix + suffix);
        return Math.min(1.0, 0.35 * edit + 0.45 * outline + 0.20 * longestBlock);
    }

    private static int levenshtein(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) row[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int previousDiagonal = row[0]; row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int old = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), previousDiagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                previousDiagonal = old;
            }
        }
        return row[b.length()];
    }
    private static int commonPrefix(String a, String b) { int i = 0; while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++; return i; }
    private static int commonSuffix(String a, String b) { int i = 0; while (i < a.length() && i < b.length() && a.charAt(a.length()-1-i) == b.charAt(b.length()-1-i)) i++; return i; }
    private static int longestCommonSubstring(String a, String b) {
        int best = 0; int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) { dp[i][j] = dp[i - 1][j - 1] + 1; best = Math.max(best, dp[i][j]); }
        }
        return best;
    }
}
