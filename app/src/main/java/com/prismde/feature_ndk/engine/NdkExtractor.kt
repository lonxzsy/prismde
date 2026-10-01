package com.prismde.feature_ndk.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class NdkExtractor {

    enum class ArchiveFormat {
        GZIP,
        XZ,
        ZIP,
        TAR,
        UNKNOWN
    }

    suspend fun extract(
        archiveFile: File,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!archiveFile.exists() || archiveFile.length() == 0L) {
            return@withContext false
        }
        targetDir.mkdirs()

        val format = detectFormat(archiveFile)
        try {
            when (format) {
                ArchiveFormat.GZIP -> {
                    extractTarStream(
                        GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile))),
                        targetDir,
                        onProgress
                    )
                }
                ArchiveFormat.XZ -> {
                    extractTarStream(
                        XZInputStream(BufferedInputStream(FileInputStream(archiveFile))),
                        targetDir,
                        onProgress
                    )
                }
                ArchiveFormat.TAR -> {
                    extractTarStream(
                        BufferedInputStream(FileInputStream(archiveFile)),
                        targetDir,
                        onProgress
                    )
                }
                ArchiveFormat.ZIP -> {
                    extractZip(archiveFile, targetDir, onProgress)
                }
                ArchiveFormat.UNKNOWN -> {
                    throw IllegalArgumentException("Неизвестный или неподдерживаемый формат архива: ${archiveFile.name}")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun detectFormat(file: File): ArchiveFormat {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(6)
                val read = fis.read(header)
                if (read >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
                    return ArchiveFormat.GZIP
                }
                if (read >= 6 &&
                    header[0] == 0xFD.toByte() &&
                    header[1] == '7'.code.toByte() &&
                    header[2] == 'z'.code.toByte() &&
                    header[3] == 'X'.code.toByte() &&
                    header[4] == 'Z'.code.toByte() &&
                    header[5] == 0x00.toByte()
                ) {
                    return ArchiveFormat.XZ
                }
                if (read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
                    return ArchiveFormat.ZIP
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to filename extension
        return when {
            file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz") -> ArchiveFormat.GZIP
            file.name.endsWith(".tar.xz") -> ArchiveFormat.XZ
            file.name.endsWith(".zip") -> ArchiveFormat.ZIP
            file.name.endsWith(".tar") -> ArchiveFormat.TAR
            else -> ArchiveFormat.UNKNOWN
        }
    }

    private fun extractTarStream(
        decompressedStream: InputStream,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ) {
        val tarIn = TarArchiveInputStream(decompressedStream)
        val deferredSymlinks = mutableListOf<Pair<File, String>>()

        tarIn.use { tar ->
            var entry = tar.nextEntry
            var count = 0
            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                // Prevent Zip Slip / directory traversal attack
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Обнаружена попытка выхода за пределы каталога: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else if (entry.isSymbolicLink || entry.isLink) {
                    outFile.parentFile?.mkdirs()
                    val linkTarget = entry.linkName
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    var symlinkCreated = false
                    try {
                        android.system.Os.symlink(linkTarget, outFile.absolutePath)
                        symlinkCreated = true
                    } catch (t: Throwable) {
                        // Os.symlink might fail on non-Linux filesystem or if unsupported
                    }
                    if (!symlinkCreated) {
                        deferredSymlinks.add(outFile to linkTarget)
                    }
                } else {
                    outFile.parentFile?.mkdirs()
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    FileOutputStream(outFile).use { output ->
                        tar.copyTo(output)
                    }

                    if (isExecutableEntry(outFile)) {
                        outFile.setExecutable(true, false)
                    }
                }

                count++
                if (count % 150 == 0) {
                    onProgress("Распаковка: извлечено $count файлов...")
                }

                entry = tar.nextEntry
            }
        }

        // Process any deferred symlinks (fallback copy if symlinks not supported on filesystem)
        for ((linkFile, targetPath) in deferredSymlinks) {
            if (linkFile.exists()) continue
            try {
                val targetFile = if (File(targetPath).isAbsolute) {
                    File(targetPath)
                } else {
                    File(linkFile.parentFile, targetPath)
                }
                if (targetFile.exists() && targetFile.isFile) {
                    targetFile.copyTo(linkFile, overwrite = true)
                    if (isExecutableEntry(linkFile)) {
                        linkFile.setExecutable(true, false)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractZip(
        archiveFile: File,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ) {
        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            var count = 0
            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Обнаружена попытка выхода за пределы каталога: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    FileOutputStream(outFile).use { output ->
                        zipIn.copyTo(output)
                    }
                    if (isExecutableEntry(outFile)) {
                        outFile.setExecutable(true, false)
                    }
                }

                count++
                if (count % 150 == 0) {
                    onProgress("Распаковка: извлечено $count файлов...")
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private fun isExecutableEntry(file: File): Boolean {
        val name = file.name
        val parent = file.parentFile?.name
        return name == "ndk-build" ||
               name == "ndk-build-android" ||
               name.startsWith("clang") ||
               name.endsWith("-clang") ||
               name.endsWith("-clang++") ||
               name == "llvm-ar" ||
               name == "llvm-nm" ||
               name == "llvm-strip" ||
               name == "ld.lld" ||
               name == "lld" ||
               name == "busybox" ||
               name == "make" ||
               name == "sh" ||
               parent == "bin"
    }
}
