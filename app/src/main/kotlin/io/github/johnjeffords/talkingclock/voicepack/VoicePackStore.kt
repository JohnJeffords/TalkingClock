package io.github.johnjeffords.talkingclock.voicepack

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.github.johnjeffords.talkingclock.domain.voicepack.VoicePackManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs and manages voice packs on disk (docs/VOICE_PACKS.md).
 *
 * Import path: the user picks a `.tcvoice` (a plain zip) with the system
 * file picker — Storage Access Framework, so NO storage permission — and we
 * stream it into app-private storage at `files/voicepacks/<id>/`, but only
 * after the manifest validates and every entry passes the zip-slip check.
 * Clip durations are measured once at import (SoundPool can't report them)
 * and stored alongside as `durations.json` for the player's stitching.
 */
class VoicePackStore(private val context: Context) {

    /** An installed pack: its directory name (the stable id) + manifest. */
    data class InstalledPack(
        val id: String,
        val manifest: VoicePackManifest,
        /** Clip token → duration in ms, measured at import. */
        val durationsMs: Map<String, Long>,
    )

    private val packsDir: File get() = File(context.filesDir, "voicepacks")

    /**
     * Import a pack from [uri]. Returns the installed pack or a failure
     * with a human-readable reason (shown verbatim on the Voice screen).
     * Runs on IO — call from a coroutine.
     */
    suspend fun import(uri: Uri): Result<InstalledPack> = withContext(Dispatchers.IO) {
        runCatching {
            // Pass 1: read the whole zip into memory-bounded temp files —
            // streamed, size-capped, names sanitized. We extract to a temp
            // dir first so a failed validation never leaves a half-pack.
            val tempDir = File(packsDir, ".importing").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                var totalBytes = 0L
                var manifestJson: String? = null

                context.contentResolver.openInputStream(uri).use { raw ->
                    requireNotNull(raw) { "Couldn't open the selected file" }
                    ZipInputStream(raw.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                require(VoicePackManifest.isSafeRelativePath(entry.name)) {
                                    "Unsafe path in pack: ${entry.name}"
                                }
                                val outFile = File(tempDir, entry.name)
                                // Belt-and-suspenders zip-slip check on the
                                // resolved path, not just the entry string.
                                require(
                                    outFile.canonicalPath
                                        .startsWith(tempDir.canonicalPath + File.separator),
                                ) { "Unsafe path in pack: ${entry.name}" }
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    val copied = zip.copyTo(out)
                                    totalBytes += copied
                                    require(totalBytes <= MAX_PACK_BYTES) {
                                        "Pack exceeds the ${MAX_PACK_BYTES / 1_000_000} MB limit"
                                    }
                                }
                                if (entry.name == "pack.json") {
                                    manifestJson = outFile.readText()
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }

                val manifest = VoicePackManifest
                    .parse(requireNotNull(manifestJson) { "The pack has no pack.json" })
                    .getOrThrow()

                // Every clip the manifest references must exist in the zip.
                manifest.clips.forEach { (token, path) ->
                    require(File(tempDir, path).isFile) {
                        "Manifest references missing clip '$path' (token $token)"
                    }
                }

                // Measure clip durations once, for the player's stitching.
                val durations = manifest.clips.mapValues { (_, path) ->
                    measureDurationMs(File(tempDir, path))
                }
                File(tempDir, DURATIONS_FILE).writeText(
                    org.json.JSONObject(durations as Map<*, *>).toString(),
                )

                // Atomically move into place under a filesystem-safe id.
                val id = sanitizeId(manifest.name)
                val finalDir = File(packsDir, id)
                finalDir.deleteRecursively()
                require(tempDir.renameTo(finalDir)) { "Couldn't install the pack" }

                InstalledPack(id, manifest, durations)
            } finally {
                File(packsDir, ".importing").deleteRecursively()
            }
        }
    }

    /** All installed packs (invalid leftovers are skipped, not fatal). */
    suspend fun listInstalled(): List<InstalledPack> = withContext(Dispatchers.IO) {
        packsDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .mapNotNull { dir -> loadPack(dir.name) }
    }

    /** Load one installed pack by id, or null if missing/corrupt. */
    suspend fun loadPack(id: String): InstalledPack? = withContext(Dispatchers.IO) {
        val dir = File(packsDir, id)
        val manifestFile = File(dir, "pack.json")
        if (!manifestFile.isFile) return@withContext null
        val manifest = VoicePackManifest.parse(manifestFile.readText())
            .getOrNull() ?: return@withContext null
        val durations = runCatching {
            val json = org.json.JSONObject(File(dir, DURATIONS_FILE).readText())
            buildMap {
                json.keys().forEach { key -> put(key, json.getLong(key)) }
            }
        }.getOrDefault(emptyMap())
        InstalledPack(id, manifest, durations)
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        File(packsDir, id).deleteRecursively()
    }

    /** Absolute file for a clip token of an installed pack. */
    fun clipFile(pack: InstalledPack, token: String): File? =
        pack.manifest.clips[token]?.let { File(File(packsDir, pack.id), it) }

    private fun measureDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: DEFAULT_CLIP_MS
        } catch (_: Exception) {
            DEFAULT_CLIP_MS // unreadable metadata: assume a beat, don't fail import
        } finally {
            retriever.release()
        }
    }

    private fun sanitizeId(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "pack" }

    companion object {
        private const val MAX_PACK_BYTES = 50L * 1_000_000 // spec: 50 MB cap
        private const val DURATIONS_FILE = "durations.json"

        /** When a clip's duration can't be read, assume this much. */
        const val DEFAULT_CLIP_MS = 600L
    }
}
