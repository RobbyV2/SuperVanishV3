/*
 * SuperVanish - MIT License
 *
 * Copyright (c) 2025 Philipp Hechler
 * Copyright (c) 2026 SuperVanish contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.philippcmd.supervanish.storage;

import dev.philippcmd.supervanish.library.VanishStateStore;
import dev.philippcmd.supervanish.library.VanishTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The plugin's own {@code players.yml} implementation of {@link VanishStateStore}.
 *
 * <p>This is where the original {@code VanishManager}'s persistence ended up. It is
 * deliberately in the plugin rather than the library: an embedding host has its own
 * storage and should not inherit a second YAML file it did not ask for.
 *
 * <p>Reads the legacy {@code vanished} / {@code super-vanished} string lists so an
 * upgrade from 3.3 keeps everyone's state.
 */
public final class YamlVanishStateStore implements VanishStateStore {

    private record Record(VanishTier tier, long since, Set<UUID> viewers) {
    }

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Record> records = new LinkedHashMap<>();

    public YamlVanishStateStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(this.file);

        // Legacy 3.3 layout: two flat lists of UUID strings.
        readLegacyList(configuration.getStringList("vanished"), VanishTier.NORMAL);
        readLegacyList(configuration.getStringList("super-vanished"), VanishTier.SILENT);

        ConfigurationSection section = configuration.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            ConfigurationSection body = section.getConfigurationSection(key);
            if (body == null) {
                continue;
            }
            VanishTier tier = VanishTier.parse(body.getString("tier"), VanishTier.SILENT);
            long since = body.getLong("since");
            Set<UUID> viewers = new LinkedHashSet<>();
            for (String viewer : body.getStringList("viewers")) {
                UUID viewerUuid = parseUuid(viewer);
                if (viewerUuid != null) {
                    viewers.add(viewerUuid);
                }
            }
            this.records.put(uuid, new Record(tier, since, viewers));
        }
    }

    private void readLegacyList(List<String> values, VanishTier tier) {
        for (String value : values) {
            UUID uuid = parseUuid(value);
            if (uuid != null) {
                this.records.put(uuid, new Record(tier, 0L, new LinkedHashSet<>()));
            }
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean isVanished(UUID uuid) {
        return this.records.containsKey(uuid);
    }

    @Override
    public VanishTier tier(UUID uuid) {
        Record record = this.records.get(uuid);
        return record == null ? null : record.tier();
    }

    @Override
    public Collection<UUID> vanished() {
        return Set.copyOf(this.records.keySet());
    }

    @Override
    public Set<UUID> viewers(UUID uuid) {
        Record record = this.records.get(uuid);
        return record == null ? Set.of() : Set.copyOf(record.viewers());
    }

    @Override
    public void put(UUID uuid, VanishTier tier, long since) {
        Record existing = this.records.get(uuid);
        Set<UUID> viewers = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing.viewers());
        this.records.put(uuid, new Record(tier, existing == null ? since : existing.since(), viewers));
    }

    @Override
    public void remove(UUID uuid) {
        this.records.remove(uuid);
    }

    @Override
    public void addViewer(UUID uuid, UUID viewer) {
        Record record = this.records.get(uuid);
        if (record != null) {
            record.viewers().add(viewer);
        }
    }

    @Override
    public void removeViewer(UUID uuid, UUID viewer) {
        Record record = this.records.get(uuid);
        if (record != null) {
            record.viewers().remove(viewer);
        }
    }

    @Override
    public void flush() {
        FileConfiguration configuration = new YamlConfiguration();
        this.records.forEach((uuid, record) -> {
            String base = "players." + uuid;
            configuration.set(base + ".tier", record.tier().serialised());
            configuration.set(base + ".since", record.since());
            configuration.set(base + ".viewers", record.viewers().stream().map(UUID::toString).toList());
        });
        try {
            File parent = this.file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            configuration.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Failed to save players.yml: " + e.getMessage());
        }
    }
}
