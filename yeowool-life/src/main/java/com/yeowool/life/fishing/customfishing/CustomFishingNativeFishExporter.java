package com.yeowool.life.fishing.customfishing;

import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishSpecies;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * CustomFishing이 켜져 있으면 실제 낚시 메커닉 자체는 그쪽으로 완전히 넘어가서
 * ({@code YeowoolLife}가 자체 {@code FishingListener}를 등록하지 않음), config.yml의
 * {@code fishing.rarities}에 정의된 우리 물고기(참치/송어 등, fishing_expansion
 * ItemsAdder 팩 아이콘)는 그 무엇으로도 실제로 잡을 방법이 없어져 버림 — 도감/지급 GUI
 * 에는 여전히 나오지만 순수 표시용일 뿐이었음.
 *
 * <p>그래서 서버 시작 시마다 그 물고기들을 CustomFishing 자체의 아이템/loot 설정 파일
 * (다른 관리 화면과 안 겹치게 {@code yw_} 접두사를 붙인 id로)로 통째로 내보내고,
 * CustomFishing에 리로드를 걸어 즉시 실제 낚시 loot 풀에 편입시킨다. 아이콘은
 * CustomFishing의 ItemsAdder 연동({@code material: "ItemsAdder:<id>"} 형식,
 * {@code ItemsAdderItemProvider} 참고)을 그대로 이용해서 fishing_expansion 팩 텍스쳐를
 * 그대로 쓴다 — 텍스쳐를 새로 안 만들어도 됨. config.yml 쪽 물고기 목록을 고치면 서버를
 * 재시작할 때마다 이 파일도 같이 재생성되므로, 두 군데를 따로 유지보수할 필요는 없다.
 */
public final class CustomFishingNativeFishExporter {

    private static final String FILE_NAME = "yeowool_native.yml";
    private static final String ID_PREFIX = "yw_";

    private CustomFishingNativeFishExporter() {
    }

    /** id가 이 접두사로 시작하면 우리가 내보낸 항목이라는 뜻 — {@link CustomFishingBridge}가 도감 등에서 중복 표시를 피하려고 걸러낼 때 씀. */
    public static boolean isExportedId(String id) {
        return id.startsWith(ID_PREFIX);
    }

    /** Our own species id for one of our exported loot ids ({@code yw_salmon} → {@code salmon}); other ids unchanged. */
    public static String nativeId(String id) {
        return isExportedId(id) ? id.substring(ID_PREFIX.length()) : id;
    }

    public static void export(JavaPlugin plugin, List<FishRarity> rarities) {
        Plugin customFishing = Bukkit.getPluginManager().getPlugin("CustomFishing");
        if (customFishing == null) {
            return;
        }
        File file = new File(customFishing.getDataFolder(), "contents/item/" + FILE_NAME);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        for (FishRarity rarity : rarities) {
            for (FishSpecies species : rarity.species()) {
                if (species.customFishingId() != null) {
                    // 이미 CustomFishing 자체 물고기(우리 쪽에서 병합만 해서 보여주는 것) — 내보낼 대상이 아님.
                    continue;
                }
                String key = ID_PREFIX + species.id();
                String materialValue = species.customIconId() != null
                        ? "ItemsAdder:" + species.customIconId()
                        : species.material().name().toLowerCase(Locale.ROOT);
                yaml.set(key + ".material", materialValue);
                yaml.set(key + ".show-in-fishfinder", true);
                yaml.set(key + ".display.name", "<white>" + species.name() + "</white>");
                if (!species.description().isBlank()) {
                    yaml.set(key + ".display.lore", List.of("<gray>" + species.description() + "</gray>"));
                }
                yaml.set(key + ".weight", Math.max(1, rarity.weight()));
                yaml.set(key + ".time", 20000);
                yaml.set(key + ".difficulty", "1-2");
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("CustomFishing용 자체 물고기 내보내기 실패: " + e.getMessage());
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cfishing reload");
        plugin.getLogger().info("여울 자체 물고기를 CustomFishing 낚시 loot로 내보냈습니다 (" + FILE_NAME + ").");
    }
}
