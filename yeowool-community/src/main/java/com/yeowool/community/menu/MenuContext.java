package com.yeowool.community.menu;

import com.yeowool.community.battlepass.BattlePassManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;

/** Bundles what the menu screens need, to avoid threading loose params through every GUI constructor. */
public record MenuContext(YeowoolCoreAPI core, MessageService messages, BattlePassManager battlePassManager, MenuConfig config) {
}
