package com.yeowool.core.api;

import com.yeowool.core.api.service.EconomyDataService;
import com.yeowool.core.api.service.LandStatService;
import com.yeowool.core.api.service.LogService;
import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.PlayerDataService;
import com.yeowool.core.api.service.PunishmentService;
import com.yeowool.core.api.service.SoundService;

import javax.sql.DataSource;

/**
 * Entry point every other Yeowool plugin uses to reach YeowoolCore.
 * Obtained through Bukkit's ServicesManager:
 *
 * <pre>{@code
 * YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
 * }</pre>
 *
 * so dependents only need {@code compileOnly} on this module, never a hard
 * runtime coupling to YeowoolCore's internals.
 */
public interface YeowoolCoreAPI {

    PlayerDataService playerData();

    EconomyDataService economyData();

    LandStatService landStats();

    MessageService messages();

    SoundService sounds();

    LogService logs();

    MailboxService mailbox();

    PunishmentService punishments();

    /**
     * Shared connection pool, for Yeowool plugins that need their own tables
     * (e.g. YeowoolEconomy's transaction ledger) without standing up a
     * second HikariCP pool.
     */
    DataSource dataSource();
}
