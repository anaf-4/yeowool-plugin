package com.yeowool.core;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.EconomyDataService;
import com.yeowool.core.api.service.LandStatService;
import com.yeowool.core.api.service.LogService;
import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.PlayerDataService;
import com.yeowool.core.api.service.PunishmentService;
import com.yeowool.core.api.service.SoundService;

import javax.sql.DataSource;

final class YeowoolCoreAPIImpl implements YeowoolCoreAPI {

    private final PlayerDataService playerDataService;
    private final EconomyDataService economyDataService;
    private final LandStatService landStatService;
    private final MessageService messageService;
    private final SoundService soundService;
    private final LogService logService;
    private final MailboxService mailboxService;
    private final PunishmentService punishmentService;
    private final DataSource dataSource;

    YeowoolCoreAPIImpl(PlayerDataService playerDataService,
                        EconomyDataService economyDataService,
                        LandStatService landStatService,
                        MessageService messageService,
                        SoundService soundService,
                        LogService logService,
                        MailboxService mailboxService,
                        PunishmentService punishmentService,
                        DataSource dataSource) {
        this.playerDataService = playerDataService;
        this.economyDataService = economyDataService;
        this.landStatService = landStatService;
        this.messageService = messageService;
        this.soundService = soundService;
        this.logService = logService;
        this.mailboxService = mailboxService;
        this.punishmentService = punishmentService;
        this.dataSource = dataSource;
    }

    @Override
    public PlayerDataService playerData() {
        return playerDataService;
    }

    @Override
    public EconomyDataService economyData() {
        return economyDataService;
    }

    @Override
    public LandStatService landStats() {
        return landStatService;
    }

    @Override
    public MessageService messages() {
        return messageService;
    }

    @Override
    public SoundService sounds() {
        return soundService;
    }

    @Override
    public LogService logs() {
        return logService;
    }

    @Override
    public MailboxService mailbox() {
        return mailboxService;
    }

    @Override
    public PunishmentService punishments() {
        return punishmentService;
    }

    @Override
    public DataSource dataSource() {
        return dataSource;
    }
}
