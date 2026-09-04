package com.yeowool.life.fishing;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public record FishRarity(String name, int weight, NamedTextColor color, List<FishSpecies> species) {
}
