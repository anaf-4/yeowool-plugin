package com.yeowool.life.farming.custom;

import java.util.List;

/**
 * One config-defined ItemsAdder custom crop: an ordered list of growth
 * stages, each a distinct custom block id (ItemsAdder crops advance by
 * replacing the whole block, unlike vanilla's {@code Ageable} data — see
 * {@link CustomCropTimerService}). Unlike vanilla crops (fixed 10 minutes,
 * {@link com.yeowool.life.farming.CropTimerService}), each stage here has
 * its own configurable duration since custom crops don't need to match that.
 */
public record CustomCropDefinition(String id, List<Stage> stages, long xpReward) {

    public record Stage(String blockId, int minutes) {
    }

    public boolean isFinalStage(int index) {
        return index >= stages.size() - 1;
    }
}
