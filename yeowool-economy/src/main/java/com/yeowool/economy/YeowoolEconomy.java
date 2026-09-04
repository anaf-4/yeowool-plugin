package com.yeowool.economy;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.message.MessageManager;
import com.yeowool.economy.command.BankCommand;
import com.yeowool.economy.command.MyCashCommand;
import com.yeowool.economy.command.WalletCommand;
import com.yeowool.economy.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 여울의 기본 화폐 "온" 관리: 지갑, 송금, 은행. 실제 잔액 저장은
 * YeowoolCore({@link YeowoolCoreAPI#economyData()})가 담당하고, 이 플러그인은
 * 명령어와 송금 규칙만 다룬다. 모든 변동은 Core의 EconomyDataService를 통해
 * 자동으로 로그에 남는다.
 */
public final class YeowoolEconomy extends JavaPlugin {

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        MessageManager messages = new MessageManager(this);

        var walletCommand = getCommand("돈");
        if (walletCommand != null) {
            var executor = new WalletCommand(this, core, messages);
            walletCommand.setExecutor(executor);
            walletCommand.setTabCompleter(executor);
        }

        var myCashCommand = getCommand("내캐시");
        if (myCashCommand != null) {
            myCashCommand.setExecutor(new MyCashCommand(core, messages));
        }

        var bankCommand = getCommand("은행");
        if (bankCommand != null) {
            var executor = new BankCommand(core, messages);
            bankCommand.setExecutor(executor);
            bankCommand.setTabCompleter(executor);
        }

        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            getServer().getServicesManager().register(Economy.class, new VaultEconomyProvider(core), this, ServicePriority.Normal);
            getLogger().info("Vault 경제 연동을 등록했습니다.");
        }

        getLogger().info("YeowoolEconomy가 활성화되었습니다.");
    }
}
