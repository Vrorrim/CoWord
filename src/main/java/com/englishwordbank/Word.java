package com.englishwordbank;

public record Word(String text, String firstImpression, String definition, Mastery mastery) {
    public enum Mastery {
        NEW("待学习"),
        HARD("陌生"),
        REVIEW("模糊"),
        MASTERED("掌握");

        private final String label;
        Mastery(String label) { this.label = label; }
        public String label() { return label; }

        public static Mastery fromDatabase(String value) {
            try { return value == null ? NEW : valueOf(value); }
            catch (IllegalArgumentException ignored) { return NEW; }
        }
    }
}
