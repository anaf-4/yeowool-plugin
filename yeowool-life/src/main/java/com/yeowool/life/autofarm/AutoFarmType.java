package com.yeowool.life.autofarm;

/** {@code /자동줍기권}/{@code /자동심기권} — which effect a voucher/charge count controls. */
public enum AutoFarmType {
    PICKUP("자동줍기", "자동줍기권", "autofarm.pickup.remaining", "moafarm_items:roll_exp"),
    PLANT("자동심기", "자동심기권", "autofarm.plant.remaining", "moafarm_items:roll_level");

    /** Sentinel for "무제한" — a real {@code -1} would misbehave once something starts decrementing it. */
    public static final long INFINITE = Long.MAX_VALUE / 2;

    private final String label;
    private final String voucherName;
    private final String statKey;
    private final String iconId;

    AutoFarmType(String label, String voucherName, String statKey, String iconId) {
        this.label = label;
        this.voucherName = voucherName;
        this.statKey = statKey;
        this.iconId = iconId;
    }

    public String label() {
        return label;
    }

    public String voucherName() {
        return voucherName;
    }

    /** {@code yw_player_statistics} key backing the remaining count — see {@link AutoFarmManager}. */
    public String statKey() {
        return statKey;
    }

    /** ItemsAdder custom-item id used as this voucher's icon (moafarm_items pack). */
    public String iconId() {
        return iconId;
    }
}
