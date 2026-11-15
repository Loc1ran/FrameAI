package com.team.frameai.ui

import android.content.Context
import com.team.frameai.lut.LutPack
import com.team.frameai.model.ContractJson
import com.team.frameai.model.LiveGuide
import com.team.frameai.model.ShotReview

internal val SampleGuideAssets = listOf(
    "live-guide.mock.json",
    "live-guide.ready.mock.json",
    "live-guide.noface.mock.json",
)

internal fun loadGuide(context: Context, assetName: String): LiveGuide {
    val raw = context.assets.open("contracts/$assetName").bufferedReader().use { it.readText() }
    return ContractJson.parseLiveGuide(raw)
}

internal fun loadOfflineReview(context: Context): ShotReview {
    val raw = context.assets.open("contracts/shot-review.offline.json").bufferedReader().use { it.readText() }
    return ContractJson.parseShotReview(raw)
}

internal fun sourceLabel(source: String): String = when (source) {
    "gemini" -> "Gemini"
    "demo_mock" -> "Sample"
    else -> "Frame check"
}

internal fun lutLabel(lutId: String): String {
    if (lutId == "none") return "None"
    return LutPack.looks.firstOrNull { it.id == lutId }?.name ?: lutId
}
