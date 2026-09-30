package com.yeowool.life.donation;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.donation.DonationRules.Candidate;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** {@code /기부 [순위]} for everyone, {@code /기부관리 예약|시작 [후보이름]|종료|리로드} for staff; also answers the editor's chat prompts. */
public final class DonationCommand implements CommandExecutor, TabCompleter, Listener {

    public static final String PERMISSION = "yeowool.life.donation.manage";
    private static final long PROMPT_TTL_MILLIS = 60_000;

    private record Prompt(Consumer<String> onText, long createdAt) {
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws Exception;
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final DonationService service;
    private final Executor executor;
    private final Supplier<DonationRules.Settings> reloadSettings;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public DonationCommand(JavaPlugin plugin, MessageService messages, DonationService service, Executor executor,
                           Supplier<DonationRules.Settings> reloadSettings) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
        this.reloadSettings = reloadSettings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("기부")) {
            if (args.length > 0 && args[0].equals("순위")) {
                service.ranking(sender);
            } else if (sender instanceof Player player) {
                service.open(player);
            } else {
                messages.send(sender, "general.player-only");
            }
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args.length == 0 ? "" : args[0]) {
            case "예약" -> edit(sender);
            case "시작" -> {
                String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                if (name != null && service.settings().pool().stream().noneMatch(c -> c.name().equals(name))) {
                    messages.send(sender, "donation.admin.unknown-candidate", Placeholder.unparsed("name", name));
                    return true;
                }
                async(sender, () -> service.reply(sender, switch (service.forceStart(name)) {
                    case STARTED -> "donation.admin.started";
                    case ALREADY_ACTIVE -> "donation.admin.already-active";
                    case NO_CANDIDATE -> "donation.admin.no-candidate";
                }));
            }
            case "종료" -> async(sender, () -> service.reply(sender, service.forceEnd() ? "donation.admin.ended" : "donation.admin.none"));
            case "리로드" -> {
                service.setSettings(reloadSettings.get());
                messages.send(sender, "donation.admin.reloaded",
                        Placeholder.unparsed("count", String.valueOf(service.settings().pool().size())));
            }
            default -> messages.send(sender, "donation.admin.usage");
        }
        return true;
    }

    private void edit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        async(sender, () -> {
            Optional<Candidate> existing = service.repository().schedule();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    new DonationEditGui(messages, this::ask, this::save, existing.orElse(null)).open(player);
                }
            });
        });
    }

    private void save(Player player, Candidate candidate) {
        async(player, () -> {
            service.repository().saveSchedule(candidate);
            service.reply(player, candidate == null ? "donation.admin.schedule-cleared" : "donation.admin.schedule-saved",
                    Placeholder.unparsed("name", candidate == null ? "" : candidate.name()));
        });
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "기부 관리 명령 처리 실패", e);
                service.reply(sender, "donation.error");
            }
        });
    }

    private void ask(Player player, String messageKey, Consumer<String> onText) {
        prompts.put(player.getUniqueId(), new Prompt(onText, System.currentTimeMillis()));
        messages.send(player, messageKey);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || System.currentTimeMillis() - prompt.createdAt() > PROMPT_TTL_MILLIS) {
            return; // none, or a forgotten one — let the chat line through
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                prompt.onText().accept(raw.equals("취소") ? null : raw);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("기부")) {
            return args.length == 1 ? List.of("순위") : List.of();
        }
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("예약", "시작", "종료", "리로드").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args[0].equals("시작")) {
            String typed = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            // names may have spaces: suggest the word being typed (a name starting with the typed words has it)
            return service.settings().pool().stream().map(Candidate::name).filter(name -> name.startsWith(typed))
                    .map(name -> name.split(" ")[args.length - 2]).distinct().toList();
        }
        return List.of();
    }
}
