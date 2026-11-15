package com.team.frameai.guide

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class JournalEntry(
    val id: String,
    val photoPath: String,
    val capturedAt: String,
    val style: String,
    val trial: String = "",
    val skill: String = "",
    val lesson: String,
    val feedback: String,
)

/** Local journal. Photographs stay in the files the camera already saved. */
class OdysseyJournal(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<JournalEntry> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<List<JournalEntry>>(file.readText()) }.getOrDefault(emptyList())
    }

    fun add(entry: JournalEntry): List<JournalEntry> {
        val next = listOf(entry) + load().filter { it.id != entry.id }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(next))
        return next
    }
}
