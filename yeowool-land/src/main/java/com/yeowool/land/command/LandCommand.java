package com.yeowool.land.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.land.LandManager;
import com.yeowool.land.gui.LandListGui;
import com.yeowool.land.level.LandLevelTable;
import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import com.yeowool.land.model.LandPermission;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /토지} dispatcher: 정보, 확인, 멤버, 초대, 추방, 삭제, 은행, 업그레이드,
 * 설정. Member invite/kick are immediate (no accept/deny handshake) to keep
 * v1 scope small — see the plugin plan section 4.1 for the fuller intended
 * command surface.
 *
 * <p>{@code /토지 삭제} takes no target — it disbands the *caller's own*
 * land. A target-nickname form would mean any player could disband anyone
 * else's land, which is a griefing vector, so this only ever acts on the
 * command sender.
 */
public final class LandCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final LandManager landManager;
    private final LandLevelTable levelTable;
    private final MessageService messages;
    private final Particle boundaryParticle;
    private final int boundaryDurationSeconds;
    private final int boundaryIntervalTicks;

    public LandCommand(JavaPlugin plugin, YeowoolCoreAPI core, LandManager landManager, LandLevelTable levelTable,
                        MessageService messages, Particle boundaryParticle, int boundaryDurationSeconds, int boundaryIntervalTicks) {
        this.plugin = plugin;
        this.core = core;
        this.landManager = landManager;
        this.levelTable = levelTable;
        this.messages = messages;
        this.boundaryParticle = boundaryParticle;
        this.boundaryDurationSeconds = boundaryDurationSeconds;
        this.boundaryIntervalTicks = boundaryIntervalTicks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0 || args[0].equals("정보")) {
            showInfo(player);
            return true;
        }

        Optional<Land> ownLand = landManager.getLandOwnedBy(player.getUniqueId());

        switch (args[0]) {
            case "확인" -> showBoundary(player, ownLand);
            case "멤버" -> showMembers(player, ownLand);
            case "초대" -> invite(player, ownLand, args);
            case "추방" -> kick(player, ownLand, args);
            case "권한" -> permissions(player, ownLand, args);
            case "삭제" -> disband(player, ownLand);
            case "은행" -> bank(player, ownLand, args);
            case "업그레이드" -> upgrade(player, ownLand);
            case "이름" -> name(player, ownLand, args);
            case "설정" -> settings(player, ownLand, args);
            case "관리" -> admin(player, args);
            default -> messages.send(player, "land.command-usage");
        }
        return true;
    }

    private void showInfo(Player player) {
        Optional<Land> land = landManager.getLandOwnedBy(player.getUniqueId());
        if (land.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        int level = core.landStats().getLandLevel(player.getUniqueId());
        long xp = core.landStats().getLandXp(player.getUniqueId());
        int maxChunks = levelTable.getMaxChunks(level);

        messages.send(player, "land.info",
                Placeholder.unparsed("name", displayName(land.get())),
                Placeholder.unparsed("level", String.valueOf(level)),
                Placeholder.unparsed("xp", String.valueOf(xp)),
                Placeholder.unparsed("chunks", String.valueOf(land.get().getChunkCount())),
                Placeholder.unparsed("max", String.valueOf(maxChunks)),
                Placeholder.unparsed("members", String.valueOf(land.get().getMembers().size())),
                Placeholder.unparsed("bank", String.format("%,d", land.get().getBankBalance())),
                Placeholder.unparsed("pvp", land.get().isPvpEnabled() ? "ON" : "OFF"));
    }

    /** {@code /토지 이름 [이름]} — clears the name (falls back to "이름없는 마을" everywhere it's shown) when called with no argument. */
    private void name(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length == 1) {
            landManager.setName(ownLand.get(), null);
            messages.send(player, "land.name-cleared");
            return;
        }
        String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (newName.length() < 2 || newName.length() > 16) {
            messages.send(player, "land.name-too-long");
            return;
        }
        landManager.setName(ownLand.get(), newName);
        messages.send(player, "land.name-success", Placeholder.unparsed("name", newName));
    }

    /** "이름없는 마을" fallback so ranking/info lines never show a blank name. */
    static String displayName(Land land) {
        return land.getName() != null && !land.getName().isBlank() ? land.getName() : "이름없는 마을";
    }

    private void showBoundary(Player player, Optional<Land> ownLand) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        List<ChunkKey> chunks = List.copyOf(ownLand.get().getChunks());
        double y = player.getLocation().getY();
        var world = player.getWorld();

        messages.send(player, "land.boundary-shown", Placeholder.unparsed("seconds", String.valueOf(boundaryDurationSeconds)));

        new BukkitRunnable() {
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (elapsedTicks >= boundaryDurationSeconds * 20 || !player.isOnline()) {
                    cancel();
                    return;
                }
                for (ChunkKey chunk : chunks) {
                    drawChunkOutline(player, world, chunk, y);
                }
                elapsedTicks += boundaryIntervalTicks;
            }
        }.runTaskTimer(plugin, 0L, boundaryIntervalTicks);
    }

    private void drawChunkOutline(Player player, org.bukkit.World world, ChunkKey chunk, double y) {
        int minX = chunk.x() * 16;
        int minZ = chunk.z() * 16;
        int maxX = minX + 16;
        int maxZ = minZ + 16;

        for (int x = minX; x <= maxX; x++) {
            player.spawnParticle(boundaryParticle, x, y, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(boundaryParticle, x, y, maxZ, 1, 0, 0, 0, 0);
        }
        for (int z = minZ; z <= maxZ; z++) {
            player.spawnParticle(boundaryParticle, minX, y, z, 1, 0, 0, 0, 0);
            player.spawnParticle(boundaryParticle, maxX, y, z, 1, 0, 0, 0, 0);
        }
    }

    private void showMembers(Player player, Optional<Land> ownLand) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        Land land = ownLand.get();
        String names = land.getMembers().stream()
                .map(uuid -> {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) {
                        return null;
                    }
                    Set<LandPermission> perms = land.getPermissions(uuid);
                    String tags = perms.isEmpty() ? "권한없음" : perms.stream()
                            .map(p -> p == LandPermission.BUILD ? "건축" : "상자")
                            .collect(Collectors.joining(","));
                    return name + "(" + tags + ")";
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
        messages.send(player, "land.member-list", Placeholder.unparsed("members", names.isEmpty() ? "-" : names));
    }

    private void invite(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length != 2) {
            messages.send(player, "land.invite-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "land.player-not-found");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "land.invite-self");
            return;
        }
        landManager.addMember(ownLand.get(), target.getUniqueId(), EnumSet.allOf(LandPermission.class));
        messages.send(player, "land.invite-success", Placeholder.unparsed("target", target.getName()));
        messages.send(target, "land.invited-by", Placeholder.unparsed("owner", player.getName()));
    }

    private void permissions(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length != 4 || (!args[3].equals("켜기") && !args[3].equals("끄기"))) {
            messages.send(player, "land.permission-usage");
            return;
        }
        LandPermission permission = switch (args[2]) {
            case "건축" -> LandPermission.BUILD;
            case "상자" -> LandPermission.CONTAINERS;
            default -> null;
        };
        if (permission == null) {
            messages.send(player, "land.permission-usage");
            return;
        }

        Land land = ownLand.get();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || !land.isMember(target.getUniqueId()) || land.getOwner().equals(target.getUniqueId())) {
            messages.send(player, "land.not-a-member");
            return;
        }

        Set<LandPermission> updated = new HashSet<>(land.getPermissions(target.getUniqueId()));
        if (args[3].equals("켜기")) {
            updated.add(permission);
        } else {
            updated.remove(permission);
        }
        landManager.setMemberPermissions(land, target.getUniqueId(), updated);
        messages.send(player, "land.permission-updated",
                Placeholder.unparsed("target", args[1]),
                Placeholder.unparsed("permission", args[2]),
                Placeholder.unparsed("state", args[3]));
    }

    private void kick(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length != 2) {
            messages.send(player, "land.kick-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || !ownLand.get().isMember(target.getUniqueId())
                || ownLand.get().getOwner().equals(target.getUniqueId())) {
            messages.send(player, "land.not-a-member");
            return;
        }
        landManager.removeMember(ownLand.get(), target.getUniqueId());
        messages.send(player, "land.kick-success", Placeholder.unparsed("target", args[1]));
    }

    private void disband(Player player, Optional<Land> ownLand) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        Land land = ownLand.get();
        core.landStats().removeLandId(player.getUniqueId(), land.getId());
        landManager.disbandLand(land);
        messages.send(player, "land.disband-success");
    }

    private void bank(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length == 1) {
            messages.send(player, "land.bank-balance", Placeholder.unparsed("bank", String.format("%,d", ownLand.get().getBankBalance())));
            return;
        }
        if (args.length != 3 || (!args[1].equals("입금") && !args[1].equals("출금"))) {
            messages.send(player, "land.bank-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "land.bank-invalid-amount");
            return;
        }
        if (amount <= 0) {
            messages.send(player, "land.bank-invalid-amount");
            return;
        }

        Land land = ownLand.get();
        if (args[1].equals("입금")) {
            if (!core.economyData().hasBalance(player.getUniqueId(), amount)) {
                messages.send(player, "land.bank-insufficient-wallet");
                return;
            }
            core.economyData().modifyBalance(player.getUniqueId(), -amount, "YeowoolLand", "토지 은행 입금");
            landManager.modifyBankBalance(land, amount);
            messages.send(player, "land.bank-deposit-success", Placeholder.unparsed("amount", String.format("%,d", amount)));
        } else {
            long result = landManager.modifyBankBalance(land, -amount);
            if (result < 0) {
                messages.send(player, "land.bank-insufficient-bank");
                return;
            }
            core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolLand", "토지 은행 출금");
            messages.send(player, "land.bank-withdraw-success", Placeholder.unparsed("amount", String.format("%,d", amount)));
        }
    }

    private void upgrade(Player player, Optional<Land> ownLand) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        int currentLevel = core.landStats().getLandLevel(player.getUniqueId());
        var nextTier = levelTable.nextTier(currentLevel);
        if (nextTier.isEmpty()) {
            messages.send(player, "land.upgrade-max-level");
            return;
        }
        var tier = nextTier.get();
        long currentXp = core.landStats().getLandXp(player.getUniqueId());
        if (currentXp < tier.requiredXp()) {
            messages.send(player, "land.upgrade-not-eligible",
                    Placeholder.unparsed("level", String.valueOf(tier.level())),
                    Placeholder.unparsed("xp", String.valueOf(currentXp)),
                    Placeholder.unparsed("required", String.valueOf(tier.requiredXp())));
            return;
        }

        Land land = ownLand.get();
        if (tier.cost() > 0) {
            long result = landManager.modifyBankBalance(land, -tier.cost());
            if (result < 0) {
                messages.send(player, "land.upgrade-insufficient-bank",
                        Placeholder.unparsed("cost", String.format("%,d", tier.cost())),
                        Placeholder.unparsed("bank", String.format("%,d", land.getBankBalance())));
                return;
            }
        }

        core.landStats().setLandLevel(player.getUniqueId(), tier.level());
        core.sounds().play(player, "levelup");
        messages.send(player, "land.upgrade-success", Placeholder.unparsed("level", String.valueOf(tier.level())));
        if (tier.reward() > 0) {
            landManager.modifyBankBalance(land, tier.reward());
            messages.send(player, "land.upgrade-reward", Placeholder.unparsed("reward", String.format("%,d", tier.reward())));
        }
    }

    private void settings(Player player, Optional<Land> ownLand, String[] args) {
        if (ownLand.isEmpty()) {
            messages.send(player, "land.no-land");
            return;
        }
        if (args.length != 2 || !args[1].equals("pvp")) {
            messages.send(player, "land.settings-usage");
            return;
        }
        Land land = ownLand.get();
        boolean newValue = !land.isPvpEnabled();
        landManager.setPvp(land, newValue);
        messages.send(player, "land.pvp-toggled", Placeholder.unparsed("state", newValue ? "ON" : "OFF"));
    }

    /** {@code /토지 관리 목록|삭제|이전} — {@code yeowool.land.bypass} only, for grief cleanup and moderation. */
    private void admin(Player player, String[] args) {
        if (!player.hasPermission("yeowool.land.bypass")) {
            messages.send(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(player, "land.admin-usage");
            return;
        }
        switch (args[1]) {
            case "목록" -> adminList(player, args);
            case "삭제" -> adminDelete(player, args);
            case "이전" -> adminTransfer(player, args);
            default -> messages.send(player, "land.admin-usage");
        }
    }

    private void adminList(Player player, String[] args) {
        int page = 0;
        if (args.length >= 3) {
            try {
                page = Math.max(0, Integer.parseInt(args[2]) - 1);
            } catch (NumberFormatException e) {
                messages.send(player, "land.admin-usage");
                return;
            }
        }
        if (landManager.all().isEmpty()) {
            messages.send(player, "land.admin-list-empty");
            return;
        }
        new LandListGui(core, landManager, page).open(player);
    }

    private void adminDelete(Player player, String[] args) {
        if (args.length != 3) {
            messages.send(player, "land.admin-delete-usage");
            return;
        }
        Optional<Land> found = landManager.findByShortId(args[2]);
        if (found.isEmpty()) {
            messages.send(player, "land.admin-not-found");
            return;
        }
        Land land = found.get();
        core.landStats().removeLandId(land.getOwner(), land.getId());
        landManager.disbandLand(land);
        messages.send(player, "land.admin-delete-success", Placeholder.unparsed("id", shortId(land)));
    }

    private void adminTransfer(Player player, String[] args) {
        if (args.length != 4) {
            messages.send(player, "land.admin-transfer-usage");
            return;
        }
        Optional<Land> found = landManager.findByShortId(args[2]);
        if (found.isEmpty()) {
            messages.send(player, "land.admin-not-found");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(player, "land.player-not-found");
            return;
        }

        Land land = found.get();
        UUID oldOwner = land.getOwner();
        var result = landManager.transferOwnership(land, target.getUniqueId());
        if (result == LandManager.TransferResult.TARGET_ALREADY_OWNS_LAND) {
            messages.send(player, "land.admin-transfer-target-has-land");
            return;
        }
        core.landStats().removeLandId(oldOwner, land.getId());
        core.landStats().addLandId(target.getUniqueId(), land.getId());
        messages.send(player, "land.admin-transfer-success",
                Placeholder.unparsed("id", shortId(land)),
                Placeholder.unparsed("target", args[3]));
    }

    private String shortId(Land land) {
        return land.getId().toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(
                    List.of("정보", "확인", "멤버", "초대", "추방", "권한", "삭제", "은행", "업그레이드", "이름", "설정", "관리"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0]) {
                case "초대", "추방", "권한" -> TabCompletions.filteredOnlinePlayerNames(args[1]);
                case "은행" -> TabCompletions.filter(List.of("입금", "출금"), args[1]);
                case "설정" -> TabCompletions.filter(List.of("pvp"), args[1]);
                case "관리" -> TabCompletions.filter(List.of("목록", "삭제", "이전"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equals("권한")) {
            return TabCompletions.filter(List.of("건축", "상자"), args[2]);
        }
        if (args.length == 3 && args[0].equals("관리") && args[1].equals("삭제")) {
            return TabCompletions.filter(landManager.all().stream().map(this::shortId).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equals("관리") && args[1].equals("이전")) {
            return TabCompletions.filter(landManager.all().stream().map(this::shortId).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equals("권한")) {
            return TabCompletions.filter(List.of("켜기", "끄기"), args[3]);
        }
        if (args.length == 4 && args[0].equals("관리") && args[1].equals("이전")) {
            return TabCompletions.filteredOnlinePlayerNames(args[3]);
        }
        return List.of();
    }
}
