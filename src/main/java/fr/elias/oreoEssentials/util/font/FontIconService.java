package fr.elias.oreoEssentials.util.font;

import fr.elias.oreoEssentials.OreoEssentials;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves item/block visuals into {@link Component} glyphs for Paper dialog
 * GUIs, backed by a resource-pack font keyed to Private-Use-Area codepoints in
 * {@code font-mappings.yml}.
 *
 * <p>
 * Dialog {@link io.papermc.paper.registry.data.dialog.ActionButton}s are
 * text-only — they have no item-icon field — so the only way to show an item
 * picture inside a button is to splice a font glyph into the label component.
 * This service does exactly that, and <strong>only</strong> when the
 * resource-pack font is enabled in config. When disabled (the default) every
 * resolver returns {@link Component#empty()} and callers render names only, so
 * vanilla clients without the pack never see "tofu" (missing-glyph boxes).
 *
 * <p>
 * Mapping keys use literal backslashes, e.g. {@code item\diamond_sword} and
 * {@code block\stone}; values are hex codepoints, e.g. {@code e42e}. Resolution
 * tries {@code item\<name>} first, then {@code block\<name>}. Most items map
 * cleanly; many blocks fall back to name-only, which is acceptable.
 */
public final class FontIconService {

    private static final String RESOURCE = "font-mappings.yml";
    private static final String DEFAULT_FONT = "minecraft:default";

    private final OreoEssentials plugin;

    private boolean enabled;
    private Key font;
    /** Logical key (e.g. {@code item\diamond}) → single-glyph string. */
    private final Map<String, String> glyphs = new HashMap<>();

    public FontIconService(OreoEssentials plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)reads the config toggle, font key, and the codepoint mappings. */
    public void load() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("font-icons.enabled", false);

        String fontKey = cfg.getString("font-icons.font", DEFAULT_FONT);
        Key parsed;
        try {
            parsed = Key.key(fontKey);
        } catch (Exception e) {
            plugin.getLogger().warning("[FontIcons] Invalid font key '" + fontKey
                    + "', falling back to " + DEFAULT_FONT);
            parsed = Key.key(DEFAULT_FONT);
        }
        this.font = parsed;

        glyphs.clear();
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (!file.exists() && plugin.getResource(RESOURCE) != null) {
            plugin.saveResource(RESOURCE, false);
        }
        if (!file.exists()) {
            plugin.getLogger().info("[FontIcons] No " + RESOURCE + " present; icons disabled.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("mappings");
        if (section == null) {
            plugin.getLogger().warning("[FontIcons] " + RESOURCE + " has no 'mappings' section.");
            return;
        }
        for (String key : section.getKeys(false)) {
            String hex = section.getString(key);
            if (hex == null || hex.isBlank()) {
                continue;
            }
            try {
                int codepoint = Integer.parseInt(hex.trim(), 16);
                glyphs.put(key, new String(Character.toChars(codepoint)));
            } catch (NumberFormatException ex) {
                // Skip malformed codepoints rather than aborting the whole load.
            }
        }
        plugin.getLogger().info("[FontIcons] Loaded " + glyphs.size() + " glyph(s); font="
                + font.asString() + " enabled=" + enabled);
    }

    /** Whether the resource-pack font is enabled. When false, no glyphs render. */
    public boolean isEnabled() {
        return enabled;
    }

    // ── Glyph resolvers ───────────────────────────────────────────────────────

    /**
     * The bare icon glyph for a material, or {@link Component#empty()} when the
     * font is disabled or no mapping exists. Tries {@code item\<name>} then
     * {@code block\<name>}.
     */
    public Component icon(Material material) {
        plugin.getLogger().info("[FontIcons] icon(Material) called material=" + material);
        return material == null ? Component.empty() : glyph(material.getKey().getKey());
    }

    /** As {@link #icon(Material)} but reads the material from a stack. */
    public Component icon(ItemStack stack) {
        plugin.getLogger().info("[FontIcons] icon(ItemStack) called stack=" + stack);
        return stack == null ? Component.empty() : icon(stack.getType());
    }

    /**
     * "{@code <glyph> <text>}" when a glyph resolves, otherwise just the text.
     * Never emits a leading separator when no glyph is present.
     */
    public Component label(Material material, String text, TextColor color) {
        return label(icon(material), Component.text(text, color));
    }

    /** Combines a (possibly empty) icon glyph with a prebuilt label component. */
    public Component label(Component icon, Component text) {
        if (icon == null || Component.empty().equals(icon)) {
            return text;
        }
        return Component.empty().append(icon).append(Component.space()).append(text);
    }

    /**
     * Resolves a logical name to a font-styled glyph component, trying the
     * {@code item\} namespace first and then {@code block\}. Returns
     * {@link Component#empty()} when disabled or unmapped — callers can treat an
     * empty result as "render text only".
     */
    private Component glyph(String name) {
        plugin.getLogger().info("[FontIcons] glyph() called name=" + name + " enabled=" + enabled);
        if (!enabled || name == null) {
            return Component.empty();
        }
        String itemKey = "item\\" + name;
        String blockKey = "block\\" + name;
        String mapped = glyphs.get(itemKey);
        boolean itemHit = mapped != null;
        if (!itemHit) {
            mapped = glyphs.get(blockKey);
        }
        boolean blockHit = mapped != null && !itemHit;
        Bukkit.getServer().sendMessage(Component.text("[FontIcons] lookup name=" + name
                + " itemKey=" + itemKey + " itemHit=" + itemHit
                + " blockKey=" + blockKey + " blockHit=" + blockHit
                + " result=" + (mapped == null ? "NONE" : Integer.toHexString(mapped.codePointAt(0)))));
        if (mapped == null) {
            return Component.empty();
        }
        return Component.text(mapped).font(font);
    }
}
