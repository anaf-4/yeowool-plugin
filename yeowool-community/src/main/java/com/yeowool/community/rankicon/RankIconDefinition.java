package com.yeowool.community.rankicon;

/**
 * One assignable rank icon. {@code id} must match an ItemsAdder
 * {@code font_images} key from the installed rank-icon pack (e.g.
 * {@code owner_icon}) so {@link RankIconManager} can turn it into the
 * {@code :id:} placeholder ItemsAdder replaces with the actual glyph.
 */
public record RankIconDefinition(String id, String display) {
}
