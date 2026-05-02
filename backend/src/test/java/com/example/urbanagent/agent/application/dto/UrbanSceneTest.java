package com.example.urbanagent.agent.application.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UrbanSceneTest {

    @Test
    void shouldContainAllRequiredScenes() {
        // 技术实现手册要求 16 种核心场景，当前实现 18 种（含扩展）
        assertEquals(18, UrbanScene.values().length);
    }

    @Test
    void shouldIncludeCityAppearanceScene() {
        assertNotNull(UrbanScene.CITY_APPEARANCE);
    }

    @Test
    void shouldIncludeGarbageRelatedScenes() {
        assertNotNull(UrbanScene.MESSY_STACKING);
        assertNotNull(UrbanScene.ACCUMULATED_GARBAGE);
        assertNotNull(UrbanScene.EXPOSED_GARBAGE);
        assertNotNull(UrbanScene.GARBAGE_OVERFLOW);
        assertNotNull(UrbanScene.PACKED_GARBAGE);
        assertNotNull(UrbanScene.GARBAGE_BURNING);
    }

    @Test
    void shouldIncludeCateringScene() {
        assertNotNull(UrbanScene.CATERING_GOVERNANCE);
        assertNotNull(UrbanScene.CATERING_OIL_FUME);
    }

    @Test
    void shouldIncludeWaterGreenSpaceScene() {
        assertNotNull(UrbanScene.WATER_GREEN_SPACE);
        assertNotNull(UrbanScene.RIVER_POLLUTION);
    }

    @Test
    void shouldIncludeIdleLandScene() {
        assertNotNull(UrbanScene.IDLE_LAND);
    }

    @Test
    void shouldIncludeEmergencyAndOrderScenes() {
        assertNotNull(UrbanScene.EMERGENCY_RESPONSE);
        assertNotNull(UrbanScene.CROWD_GATHERING);
        assertNotNull(UrbanScene.ROAD_WATERLOGGING);
    }

    @Test
    void shouldSupportGarbageOverflowScene() {
        // 垃圾满溢是常见治理场景
        UrbanScene scene = UrbanScene.GARBAGE_OVERFLOW;
        assertNotNull(scene);
        assertEquals("GARBAGE_OVERFLOW", scene.name());
    }

    @Test
    void shouldSupportStreetOrderScene() {
        // 街面秩序场景
        UrbanScene scene = UrbanScene.STREET_ORDER;
        assertNotNull(scene);
        assertEquals("STREET_ORDER", scene.name());
    }

    @Test
    void enumValuesShouldBeConsistentlyNamed() {
        for (UrbanScene scene : UrbanScene.values()) {
            assertNotNull(scene.name());
            assertEquals(scene.name().toUpperCase(), scene.name());
            // 验证枚举值格式：全大写，可用下划线分隔（如 CATERING_OIL_FUME）
            assertTrue(scene.name().matches("^[A-Z][A-Z_]*$"),
                    "枚举值应全大写，可用下划线分隔: " + scene.name());
        }
    }
}