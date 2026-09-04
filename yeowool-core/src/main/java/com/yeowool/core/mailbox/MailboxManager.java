package com.yeowool.core.mailbox;

import com.yeowool.core.api.model.MailboxEntry;
import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.data.repository.MailboxRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class MailboxManager implements MailboxService {

    private final JavaPlugin plugin;
    private final MailboxRepository repository;
    private final ExecutorService executor;
    private final DataSource dataSource;

    public MailboxManager(JavaPlugin plugin, MailboxRepository repository, ExecutorService executor, DataSource dataSource) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.dataSource = dataSource;
    }

    @Override
    public void deliverOrStore(UUID recipient, ItemStack item, String sourcePlugin, String note) {
        Player online = Bukkit.getPlayer(recipient);
        if (online != null) {
            var leftover = online.getInventory().addItem(item.clone());
            if (leftover.isEmpty()) {
                return;
            }
            for (ItemStack remaining : leftover.values()) {
                store(recipient, remaining, sourcePlugin, note);
            }
            return;
        }
        store(recipient, item, sourcePlugin, note);
    }

    private void store(UUID recipient, ItemStack item, String sourcePlugin, String note) {
        ItemStack toStore = item.clone();
        long createdAt = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.insert(recipient, toStore, sourcePlugin, note, createdAt);
            } catch (SQLException e) {
                plugin.getLogger().severe("우편함 저장 실패 (" + recipient + "): " + e.getMessage());
            }
        });
        // 접속 중이 아니라 우편함에 쌓이는 경우에만 알림 - 온라인 상태로 바로 받은
        // 경우는 이미 눈앞에서 확인했으니 디스코드 DM까지 보낼 필요가 없음.
        queueDiscordDm(recipient, "우편함에 새 아이템이 도착했습니다" + (note == null || note.isBlank() ? "." : ": " + note));
    }

    /**
     * {@code yw_discord_dm_queue}에 알림 요청을 적재한다 - 여울 플러그인들은 디스코드에
     * 직접 접속하지 않는다는 기존 규칙을 그대로 지켜서(YeowoolDiscord 참고), 실제 발송은
     * yeowool-web의 백그라운드 작업이 이 큐를 읽어서 처리한다.
     */
    private void queueDiscordDm(UUID recipient, String message) {
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO yw_discord_dm_queue (uuid, message, created_at) VALUES (?, ?, ?)")) {
                insert.setString(1, recipient.toString());
                insert.setString(2, message);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("디스코드 알림 큐 적재 실패 (" + recipient + "): " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<List<MailboxEntry>> loadPending(UUID recipient) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.loadByRecipient(recipient);
            } catch (SQLException e) {
                plugin.getLogger().severe("우편함 조회 실패 (" + recipient + "): " + e.getMessage());
                return List.<MailboxEntry>of();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ItemStack>> claim(UUID recipient, long entryId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.claim(entryId, recipient).map(MailboxEntry::item);
            } catch (SQLException e) {
                plugin.getLogger().severe("우편함 수령 실패 (" + recipient + "/" + entryId + "): " + e.getMessage());
                return Optional.<ItemStack>empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> tryDeliverAll(Player player) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        loadPending(player.getUniqueId()).thenAccept(entries -> {
            if (entries.isEmpty()) {
                result.complete(0);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                int delivered = 0;
                for (MailboxEntry entry : entries) {
                    if (!player.isOnline()) {
                        break;
                    }
                    var leftover = player.getInventory().addItem(entry.item().clone());
                    if (leftover.isEmpty()) {
                        delivered++;
                        executor.execute(() -> {
                            try {
                                repository.delete(entry.id());
                            } catch (SQLException e) {
                                plugin.getLogger().severe("우편함 항목 삭제 실패 (" + entry.id() + "): " + e.getMessage());
                            }
                        });
                    }
                }
                result.complete(delivered);
            });
        });
        return result;
    }
}
