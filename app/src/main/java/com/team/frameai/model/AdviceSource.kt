package com.team.frameai.model

/**
 * Frozen `source` values. Mocks must never be labeled as Gemini.
 */
enum class AdviceSource {
    OFFLINE_RULES,
    DEMO_MOCK,
    GEMINI;

    val wire: String
        get() = when (this) {
            OFFLINE_RULES -> "offline_rules"
            DEMO_MOCK -> "demo_mock"
            GEMINI -> "gemini"
        }

    companion object {
        fun fromWire(value: String): AdviceSource {
            return values().firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown source: $value")
        }
    }
}
