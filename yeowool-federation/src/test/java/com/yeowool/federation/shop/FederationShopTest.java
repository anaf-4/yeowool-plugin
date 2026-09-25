package com.yeowool.federation.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederationShopTest {

    @Test
    void readsIdNameAndMinLevel() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(
                Map.of("shop-id", "federation_basic", "name", "연합 기본 상점", "min-level", 1),
                Map.of("shop-id", "federation_rare", "name", "연합 희귀 상점", "min-level", 5)));

        assertEquals(List.of(
                new FederationShop("federation_basic", "연합 기본 상점", 1),
                new FederationShop("federation_rare", "연합 희귀 상점", 5)), shops);
    }

    @Test
    void nameDefaultsToIdAndMinLevelDefaultsToOne() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(Map.of("shop-id", "federation_basic")));

        assertEquals(List.of(new FederationShop("federation_basic", "federation_basic", 1)), shops);
    }

    @Test
    void entriesWithoutShopIdAreSkipped() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(Map.of("name", "이름만 있음", "min-level", 3)));

        assertEquals(List.of(), shops);
    }
}
