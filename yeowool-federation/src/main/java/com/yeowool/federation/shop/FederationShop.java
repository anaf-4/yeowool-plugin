package com.yeowool.federation.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One config.yml `shops:` entry — an existing /상점 shop id that only federations of minLevel+ may open. */
public record FederationShop(String shopId, String name, int minLevel) {

    public static List<FederationShop> fromConfig(List<? extends Map<?, ?>> entries) {
        List<FederationShop> shops = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            Object id = entry.get("shop-id");
            if (id == null) {
                continue;
            }
            Object name = entry.get("name");
            Object minLevel = entry.get("min-level");
            shops.add(new FederationShop(
                    id.toString(),
                    name == null ? id.toString() : name.toString(),
                    minLevel instanceof Number number ? number.intValue() : 1));
        }
        return List.copyOf(shops);
    }
}
