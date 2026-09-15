package com.yeowool.community.ranking;

/** The three leaderboards {@code /명예의전당} tracks. */
public enum RankingCategory {
    MONEY("돈", "money"),
    LAND("마을", "land"),
    PLAYTIME("접속시간", "playtime");

    private final String commandKeyword;
    private final String placeholderKeyword;

    RankingCategory(String commandKeyword, String placeholderKeyword) {
        this.commandKeyword = commandKeyword;
        this.placeholderKeyword = placeholderKeyword;
    }

    public String commandKeyword() {
        return commandKeyword;
    }

    public String placeholderKeyword() {
        return placeholderKeyword;
    }

    public static RankingCategory byCommandKeyword(String keyword) {
        for (RankingCategory category : values()) {
            if (category.commandKeyword.equals(keyword)) {
                return category;
            }
        }
        return null;
    }

    public static RankingCategory byPlaceholderKeyword(String keyword) {
        for (RankingCategory category : values()) {
            if (category.placeholderKeyword.equals(keyword)) {
                return category;
            }
        }
        return null;
    }
}
