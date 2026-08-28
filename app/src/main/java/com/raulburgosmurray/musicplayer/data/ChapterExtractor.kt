package com.raulburgosmurray.musicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.raulburgosmurray.musicplayer.Chapter
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ChapterExtractor {

    private const val TAG = "ChapterExtractor"
    private const val MAX_ATOM_DEPTH = 20
    private const val MAX_FILE_SIZE_FOR_MEMORY = 150L * 1024 * 1024

    fun extractChapters(context: Context, uriString: String): List<Chapter> {
        return try {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("content://")) {
                extractFromContentUri(context, uri)
            } else {
                extractFromLocalFile(uriString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract chapters from $uriString", e)
            emptyList()
        }
    }

    private fun extractFromContentUri(context: Context, uri: Uri): List<Chapter> {
        val tempFile = copyToTempFile(context, uri) ?: return emptyList()
        return try {
            parseChplAtom(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private fun extractFromLocalFile(path: String): List<Chapter> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        return parseChplAtom(file)
    }

    private fun copyToTempFile(context: Context, uri: Uri): File? {
        return try {
            val tempFile = File(context.cacheDir, "chapter_${System.currentTimeMillis()}.m4b")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy content URI to temp file", e)
            null
        }
    }

    private fun parseChplAtom(file: File): List<Chapter> {
        try {
            val size = file.length()
            if (size > MAX_FILE_SIZE_FOR_MEMORY) {
                return parseChplStreaming(file)
            }
            val data = file.readBytes()
            val offset = findAtomOffset(data, "chpl")
            if (offset == null) {
                return parseChplFromUdta(data)
            }
            return parseChplFromOffset(data, offset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chpl atom from ${file.name}", e)
            return emptyList()
        }
    }

    private fun parseChplFromUdta(data: ByteArray): List<Chapter> {
        val udtaOffset = findAtomOffset(data, "udta") ?: return emptyList()
        val udtaSize = readUInt32BE(data, udtaOffset).toInt()
        val udtaEnd = udtaOffset + udtaSize
        if (udtaEnd > data.size) return emptyList()

        var pos = udtaOffset + 8
        while (pos + 8 <= udtaEnd && pos + 8 <= data.size) {
            val subSize = readUInt32BE(data, pos).toInt()
            val subName = String(data, pos + 4, 4, Charsets.US_ASCII)

            if (subName == "chpl") {
                return parseChplFromOffset(data, pos)
            }
            if (subSize < 8) break
            pos += subSize
        }
        return emptyList()
    }

    private fun parseChplFromOffset(data: ByteArray, atomOffset: Int): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        try {
            val atomSize = readUInt32BE(data, atomOffset).toInt()
            val headerEnd = atomOffset + 8
            val dataEnd = atomOffset + atomSize
            if (headerEnd >= data.size) return emptyList()

            val version = data[headerEnd].toInt() and 0xFF
            var pos = headerEnd + 1

            val count = if (version == 1 && pos + 3 < data.size) {
                val c = ((data[pos].toInt() and 0xFF) shl 24) or
                        ((data[pos + 1].toInt() and 0xFF) shl 16) or
                        ((data[pos + 2].toInt() and 0xFF) shl 8) or
                        (data[pos + 3].toInt() and 0xFF)
                pos += 4
                c
            } else {
                val c = data[pos].toInt() and 0xFF
                pos += 1
                c
            }

            for (i in 0 until count) {
                if (pos + 16 > minOf(dataEnd, data.size)) break

                val startTime = readUInt64BE(data, pos)
                pos += 8

                val endTime = readUInt64BE(data, pos)
                pos += 8

                if (pos >= data.size) break
                val titleLen = data[pos].toInt() and 0xFF
                pos += 1

                val title = if (pos + titleLen <= minOf(dataEnd, data.size)) {
                    String(data, pos, titleLen, Charsets.UTF_8)
                } else {
                    "Chapter ${i + 1}"
                }
                pos += titleLen

                val durationMs = if (endTime > startTime) (endTime - startTime) else 0L
                chapters.add(Chapter(title = title, startMs = startTime / 10000, durationMs = durationMs / 10000))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chpl data", e)
        }
        return chapters.sortedBy { it.startMs }
    }

    private fun parseChplStreaming(file: File): List<Chapter> {
        try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val chplData = findAtomStreaming(raf, file.length(), "chpl") ?: return emptyList()
                return parseChplFromOffset(chplData, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming parse failed", e)
            return emptyList()
        }
    }

    private fun findAtomStreaming(raf: java.io.RandomAccessFile, fileSize: Long, targetAtom: String): ByteArray? {
        val stack = java.util.ArrayDeque<Pair<Long, Long>>()
        stack.push(Pair(0L, fileSize))

        while (stack.isNotEmpty()) {
            val (start, end) = stack.pop()
            var pos = start
            val buf = ByteArray(8)

            while (pos + 8 <= end) {
                raf.seek(pos)
                if (raf.read(buf) < 8) break
                val size = readUInt32BEFromBuf(buf, 0)
                val name = String(buf, 4, 4, Charsets.US_ASCII)

                if (name == targetAtom) {
                    val atomData = ByteArray(size.toInt())
                    raf.seek(pos)
                    raf.readFully(atomData)
                    return atomData
                }

                val containerAtoms = setOf("moov", "udta", "meta", "mdia", "minf", "stbl", "trak", "edts", "mdia")
                if (name in containerAtoms && size.toInt() > 8) {
                    stack.push(Pair(pos + 8, pos + size))
                }

                if (size < 8) break
                pos += size
            }
        }
        return null
    }

    private fun findAtomOffset(data: ByteArray, atomName: String): Int? {
        return findAtomOffsetRecursive(data, 0, data.size, atomName, 0)
    }

    private fun findAtomOffsetRecursive(data: ByteArray, start: Int, end: Int, atomName: String, depth: Int): Int? {
        if (depth > MAX_ATOM_DEPTH) return null
        var pos = start
        while (pos + 8 <= end) {
            val size = readUInt32BE(data, pos).toInt()
            val name = String(data, pos + 4, 4, Charsets.US_ASCII)

            if (name == atomName) return pos

            val containerAtoms = setOf("moov", "udta", "meta", "mdia", "minf", "stbl", "trak", "edts")
            if (name in containerAtoms && size > 8) {
                val result = findAtomOffsetRecursive(data, pos + 8, pos + size, atomName, depth + 1)
                if (result != null) return result
            }

            if (size < 8) break
            pos += size
        }
        return null
    }

    private fun readUInt32BE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
                ((data[offset + 1].toLong() and 0xFF) shl 16) or
                ((data[offset + 2].toLong() and 0xFF) shl 8) or
                (data[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt32BEFromBuf(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
                ((buf[offset + 1].toLong() and 0xFF) shl 16) or
                ((buf[offset + 2].toLong() and 0xFF) shl 8) or
                (buf[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64BE(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return result
    }
}