package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.federation.land.LandInfo;
import com.yeowool.federation.land.LandLookup;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** {@code /연합 <하위명령어>} — see messages.yml's federation.usage for the full subcommand list. */
public final class FederationCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final FederationManager manager;
    private final LandLookup landLookup;
    private final ExecutorService executor;

    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, FederationManager manager,
                              LandLookup landLookup, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.manager = manager;
        this.landLookup = landLookup;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            core.messages().send(player, "federation.usage");
            return true;
        }

        switch (args[0]) {
            case "생성" -> handleCreate(player, args);
            case "정보" -> handleInfo(player, args);
            case "목록" -> handleList(player);
            default -> core.messages().send(player, "federation.usage");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.create(land.get().id(), name);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.create-success", Placeholder.unparsed("name", name));
                        case LAND_ALREADY_IN_FEDERATION -> core.messages().send(player, "federation.already-in-federation");
                        case NAME_TAKEN -> core.messages().send(player, "federation.name-taken", Placeholder.unparsed("name", name));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 생성 실패: " + e.getMessage());
            }
        });
    }

    private void handleInfo(Player player, String[] args) {
        executor.execute(() -> {
            try {
                Optional<Federation> federation;
                if (args.length >= 2) {
                    federation = manager.findByName(args[1]);
                } else {
                    Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                    federation = land.isPresent() ? manager.findByLandId(land.get().id()) : Optional.empty();
                }
                if (federation.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.info-not-found"));
                    return;
                }
                Federation f = federation.get();
                List<FederationMember> members = manager.membersOf(f.id());
                StringBuilder memberLines = new StringBuilder();
                for (FederationMember member : members) {
                    Optional<LandInfo> landInfo = landLookup.findById(member.landId());
                    String landName = landInfo.map(LandInfo::landName).orElse("?");
                    memberLines.append("\n§7- ").append(landName).append(" (").append(member.role()).append(")");
                }
                String description = f.description() == null ? "(없음)" : f.description();
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + memberLines));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 정보 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleList(Player player) {
        executor.execute(() -> {
            try {
                List<Federation> federations = manager.listAll();
                if (federations.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.list-empty"));
                    return;
                }
                runOnMain(() -> core.messages().send(player, "federation.list-header"));
                for (Federation f : federations) {
                    int memberCount;
                    try {
                        memberCount = manager.membersOf(f.id()).size();
                    } catch (java.sql.SQLException e) {
                        memberCount = 0;
                    }
                    int finalMemberCount = memberCount;
                    runOnMain(() -> core.messages().send(player, "federation.list-line",
                            Placeholder.unparsed("name", f.name()),
                            Placeholder.unparsed("level", String.valueOf(f.level())),
                            Placeholder.unparsed("count", String.valueOf(finalMemberCount))));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
