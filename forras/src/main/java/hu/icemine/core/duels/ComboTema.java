package hu.icemine.core.duels;

import hu.icemine.core.build.Build;
import java.util.HashMap;
import java.util.Map;

/** A ComboFly kupola anyagai más témában (jég az eredeti). */
final class ComboTema {

    private static final Map<String, Map<String, String>> TEMAK = new HashMap<>();

    static {
        TEMAK.put("nether", Map.ofEntries(
                Map.entry("packed_ice", "nether_bricks"), Map.entry("blue_ice", "red_nether_bricks"),
                Map.entry("snow_block", "netherrack"), Map.entry("calcite", "blackstone"),
                Map.entry("white_concrete", "polished_blackstone"), Map.entry("smooth_quartz", "polished_blackstone_bricks"),
                Map.entry("light_blue_concrete", "crimson_nylium"), Map.entry("cyan_concrete", "warped_nylium"),
                Map.entry("sea_lantern", "shroomlight"), Map.entry("light_blue_stained_glass", "red_stained_glass"),
                Map.entry("white_stained_glass", "black_stained_glass"), Map.entry("glass", "orange_stained_glass"),
                Map.entry("stone", "netherrack"), Map.entry("diorite", "basalt"), Map.entry("quartz_bricks", "nether_bricks"),
                Map.entry("polished_diorite", "polished_blackstone"), Map.entry("quartz", "chiseled_nether_bricks")));
        TEMAK.put("desert", Map.ofEntries(
                Map.entry("packed_ice", "sandstone"), Map.entry("blue_ice", "cut_sandstone"),
                Map.entry("snow_block", "smooth_sandstone"), Map.entry("calcite", "smooth_sandstone"),
                Map.entry("white_concrete", "cut_sandstone"), Map.entry("smooth_quartz", "chiseled_sandstone"),
                Map.entry("light_blue_concrete", "orange_terracotta"), Map.entry("cyan_concrete", "terracotta"),
                Map.entry("sea_lantern", "glowstone"), Map.entry("light_blue_stained_glass", "yellow_stained_glass"),
                Map.entry("white_stained_glass", "white_stained_glass"), Map.entry("glass", "orange_stained_glass"),
                Map.entry("stone", "sandstone"), Map.entry("diorite", "red_sandstone"), Map.entry("quartz_bricks", "smooth_sandstone"),
                Map.entry("polished_diorite", "cut_red_sandstone"), Map.entry("quartz", "chiseled_red_sandstone")));
        TEMAK.put("forest", Map.ofEntries(
                Map.entry("packed_ice", "mossy_stone_bricks"), Map.entry("blue_ice", "stone_bricks"),
                Map.entry("snow_block", "grass_block"), Map.entry("calcite", "moss_block"),
                Map.entry("white_concrete", "grass_block"), Map.entry("smooth_quartz", "coarse_dirt"),
                Map.entry("light_blue_concrete", "lime_concrete"), Map.entry("cyan_concrete", "green_concrete"),
                Map.entry("sea_lantern", "shroomlight"), Map.entry("light_blue_stained_glass", "lime_stained_glass"),
                Map.entry("white_stained_glass", "green_stained_glass"), Map.entry("glass", "glass"),
                Map.entry("stone", "stone"), Map.entry("diorite", "andesite"), Map.entry("quartz_bricks", "stone_bricks"),
                Map.entry("polished_diorite", "polished_andesite"), Map.entry("quartz", "chiseled_stone_bricks")));
    }

    private ComboTema() {
    }

    static Build.Sink sink(Build.Sink inner, String tema) {
        Map<String, String> map = TEMAK.get(tema);
        if (map == null) {
            return inner;
        }
        return (x, y, z, data) -> {
            String base = data;
            String state = "";
            int bracket = data.indexOf('[');
            if (bracket > 0) {
                base = data.substring(0, bracket);
                state = data.substring(bracket);
            }
            if (base.equals("snow")) {
                inner.set(x, y, z, "air");
                return;
            }
            String to = map.get(base);
            inner.set(x, y, z, to == null ? data : (to.equals("basalt") ? "basalt[axis=y]" : to + (base.equals("quartz_pillar") ? state : "")));
        };
    }
}
