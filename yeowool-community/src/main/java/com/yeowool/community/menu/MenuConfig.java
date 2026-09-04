package com.yeowool.community.menu;

import org.bukkit.configuration.file.FileConfiguration;

/** Loads the {@code menu:} config.yml section. */
public final class MenuConfig {

    private final int hubBackgroundOffsetPx;
    private final int categoryBackgroundOffsetPx;

    private MenuConfig(int hubBackgroundOffsetPx, int categoryBackgroundOffsetPx) {
        this.hubBackgroundOffsetPx = hubBackgroundOffsetPx;
        this.categoryBackgroundOffsetPx = categoryBackgroundOffsetPx;
    }

    public static MenuConfig load(FileConfiguration config) {
        return new MenuConfig(
                config.getInt("menu.gui-background-offset", -8),
                config.getInt("menu.gui-background-offset-categories", -8));
    }

    /** {@code /메뉴} 메인 허브(menu_bg_hub_v2) 배경의 좌우 위치 보정값. */
    public int hubBackgroundOffsetPx() {
        return hubBackgroundOffsetPx;
    }

    /** 카테고리 서브 화면(menu_bg_18_v2/27_v2/rewards_v2 등) 배경의 좌우 위치 보정값 - 전부 공유. */
    public int categoryBackgroundOffsetPx() {
        return categoryBackgroundOffsetPx;
    }
}
