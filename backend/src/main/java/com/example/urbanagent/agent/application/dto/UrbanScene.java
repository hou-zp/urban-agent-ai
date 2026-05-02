package com.example.urbanagent.agent.application.dto;

/**
 * 城市治理场景枚举，覆盖城市风貌提升主要治理场景。
 * 与技术实现手册保持一致，支持 16 种核心场景。
 */
public enum UrbanScene {
    // 市容环境类
    CITY_APPEARANCE,        // 市容环境（立面、户外广告、占道经营）
    STREET_ORDER,          // 街面秩序（摊贩、共享单车、占道撑伞）

    // 环卫保洁类
    ENV_SANITATION,         // 环境卫生（日常保洁、环卫设施）
    MESSY_STACKING,        // 乱堆物堆料
    ACCUMULATED_GARBAGE,   // 积存垃圾渣土
    EXPOSED_GARBAGE,       // 暴露垃圾
    GARBAGE_OVERFLOW,      // 垃圾满溢
    PACKED_GARBAGE,        // 打包垃圾
    GARBAGE_BURNING,       // 焚烧垃圾

    // 餐饮污染类
    CATERING_GOVERNANCE,   // 餐饮综合治理
    CATERING_OIL_FUME,     // 餐饮油烟

    // 水绿环境类
    WATER_GREEN_SPACE,     // 水绿空间（绿地、河道）
    RIVER_POLLUTION,       // 河道污染（河道垃圾、漂浮物）

    // 空间治理类
    URBAN_SPACE,           // 城市空间（停车、围挡）
    IDLE_LAND,             // 空闲地块

    // 应急与秩序类
    EMERGENCY_RESPONSE,    // 应急响应（突发、险情）
    CROWD_GATHERING,       // 人群聚集
    ROAD_WATERLOGGING      // 道路积水
}
