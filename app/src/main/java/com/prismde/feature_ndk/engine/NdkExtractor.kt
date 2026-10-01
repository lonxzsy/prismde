package com.prismde.feature_ndk.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class NdkExtractor {

    suspend fun extract(
        archiveFile: File,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        targetDir.mkdirs()

        try {
            when {
                archiveFile.name.endsWith(".tar.xz") -> extractTarXz(archiveFile, targetDir, onProgress)
                archiveFile.name.endsWith(".zip") -> extractZip(archiveFile, targetDir, onProgress)
                else -> throw IllegalArgumentException("Неподдерживаемый формат архива: ${archiveFile.name}")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractTarXz(
        archiveFile: File,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ) {
        val fis = FileInputStream(archiveFile)
        val bis = BufferedInputStream(fis)
        val xzIn = XZInputStream(bis)
        val tarIn = TarArchiveInputStream(xzIn)

        tarIn.use { tar ->
            var entry = tar.nextTarEntry
            var count = 0
            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                // Prevent Zip Slip / directory traversal attack
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Обнаружена попытка выхода за пределы каталога: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        tar.copyTo(output)
                    }

                    // Preserve executable permissions on POSIX systems
                    if (entry.mode and 0b001_000_000 != 0 || outFile.name.endsWith("clang") || outFile.name.endsWith("clang++")) {
                        outFile.setExecutable(true, false)
                    }
                }

                count++
                if (count % 200 == 0) {
                    onProgress("Распаковка: извлечено $count файлов...")
                }

                entry = tar.nextTarEntry
            }
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
                    FileOutputStream(outFile).use { output ->
                        zipIn.copyTo(output)
                    }
                    if (outFile.name.endsWith("clang") || outFile.name.endsWith("clang++") || outFile.parentFile?.name == "bin") {
                        outFile.setExecutable(true, false)
                    }
                }

                count++
                if (count % 200 == 0) {
                    onProgress("Распаковка: извлечено $count файлов...")
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
