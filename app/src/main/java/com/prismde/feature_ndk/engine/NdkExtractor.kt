package com.prismde.feature_ndk.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
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
        DEB,
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
                ArchiveFormat.DEB -> {
                    extractDeb(archiveFile, targetDir, onProgress)
                }
                ArchiveFormat.UNKNOWN -> {
                    val isRu = java.util.Locale.getDefault().language == "ru"
                    throw IllegalArgumentException(if (isRu) "Неизвестный или неподдерживаемый формат архива: ${archiveFile.name}" else "Unknown or unsupported archive format: ${archiveFile.name}")
                }
            }
            val isRu = java.util.Locale.getDefault().language == "ru"
            onProgress(if (isRu) "Настройка прав доступа к файлам компилятора..." else "Configuring compiler toolchain permissions...")
            com.prismde.core.model.NdkVersion.ensureNdkPermissions(targetDir)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun detectFormat(file: File): ArchiveFormat {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8)
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
                if (read >= 7 &&
                    header[0] == '!'.code.toByte() &&
                    header[1] == '<'.code.toByte() &&
                    header[2] == 'a'.code.toByte() &&
                    header[3] == 'r'.code.toByte() &&
                    header[4] == 'c'.code.toByte() &&
                    header[5] == 'h'.code.toByte() &&
                    header[6] == '>'.code.toByte()
                ) {
                    return ArchiveFormat.DEB
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to filename extension
        return when {
            file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz") -> ArchiveFormat.GZIP
            file.name.endsWith(".tar.xz") -> ArchiveFormat.XZ
            file.name.endsWith(".deb") -> ArchiveFormat.DEB
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
                    val isRu = java.util.Locale.getDefault().language == "ru"
                    throw SecurityException(if (isRu) "Обнаружена попытка выхода за пределы каталога: ${entry.name}" else "Path traversal attack detected: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else if (entry.isSymbolicLink || entry.isLink) {
                    outFile.parentFile?.mkdirs()
                    val linkTarget = entry.linkName
                    try { outFile.delete() } catch (_: Throwable) {}
                    try { android.system.Os.remove(outFile.absolutePath) } catch (_: Throwable) {}
                    var symlinkCreated = false
                    try {
                        android.system.Os.symlink(linkTarget, outFile.absolutePath)
                        symlinkCreated = true
                    } catch (_: Throwable) {
                        try {
                            java.nio.file.Files.createSymbolicLink(outFile.toPath(), java.nio.file.Paths.get(linkTarget))
                            symlinkCreated = true
                        } catch (_: Throwable) {}
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
                if (count % 1000 == 0) {
                    val isRu = java.util.Locale.getDefault().language == "ru"
                    onProgress(if (isRu) "Распаковка: извлечено $count файлов..." else "Unpacking: $count files extracted...")
                }

                entry = tar.nextEntry
            }
        }

        // Process any deferred symlinks in multiple passes (fallback copy if symlinks not supported on filesystem)
        var remainingSymlinks = deferredSymlinks.toMutableList()
        for (pass in 1..3) {
            if (remainingSymlinks.isEmpty()) break
            val unresolved = mutableListOf<Pair<File, String>>()
            for ((linkFile, targetPath) in remainingSymlinks) {
                try {
                    try { linkFile.delete() } catch (_: Throwable) {}
                    try { android.system.Os.remove(linkFile.absolutePath) } catch (_: Throwable) {}
                    var symlinked = false
                    try {
                        android.system.Os.symlink(targetPath, linkFile.absolutePath)
                        symlinked = true
                    } catch (_: Throwable) {
                        try {
                            java.nio.file.Files.createSymbolicLink(linkFile.toPath(), java.nio.file.Paths.get(targetPath))
                            symlinked = true
                        } catch (_: Throwable) {}
                    }
                    if (symlinked) continue

                    val targetFile = if (File(targetPath).isAbsolute) {
                        File(targetPath)
                    } else {
                        File(linkFile.parentFile, targetPath)
                    }
                    if (targetFile.exists()) {
                        if (targetFile.isFile) {
                            targetFile.copyTo(linkFile, overwrite = true)
                            if (isExecutableEntry(linkFile)) {
                                linkFile.setExecutable(true, false)
                            }
                        } else if (targetFile.isDirectory) {
                            targetFile.copyRecursively(linkFile, overwrite = true)
                        }
                    } else {
                        unresolved.add(linkFile to targetPath)
                    }
                } catch (_: Exception) {}
            }
            remainingSymlinks = unresolved
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
                    val isRu = java.util.Locale.getDefault().language == "ru"
                    throw SecurityException(if (isRu) "Обнаружена попытка выхода за пределы каталога: ${entry.name}" else "Path traversal attack detected: ${entry.name}")
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
                if (count % 1000 == 0) {
                    val isRu = java.util.Locale.getDefault().language == "ru"
                    onProgress(if (isRu) "Распаковка: извлечено $count файлов..." else "Unpacking: $count files extracted...")
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    private fun extractDeb(
        archiveFile: File,
        targetDir: File,
        onProgress: (statusMessage: String) -> Unit
    ) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        onProgress(if (isRu) "Анализ пакета Debian (.deb)..." else "Analyzing Debian package (.deb)...")

        ArArchiveInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { ar ->
            var entry = ar.nextEntry
            var foundData = false
            while (entry != null) {
                val name = entry.name.trim()
                if (name.startsWith("data.tar")) {
                    foundData = true
                    onProgress(if (isRu) "Распаковка содержимого пакета OpenJDK ($name)..." else "Extracting OpenJDK package data ($name)...")
                    val decompressedStream: InputStream = when {
                        name.contains(".xz") -> XZInputStream(ar)
                        name.contains(".gz") -> GZIPInputStream(ar)
                        else -> ar
                    }
                    extractTarStream(decompressedStream, targetDir, onProgress)
                    break
                }
                entry = ar.nextEntry
            }
            if (!foundData) {
                throw IllegalStateException(
                    if (isRu) "В архиве .deb не найден файл данных data.tar.*"
                    else "data.tar.* not found inside .deb archive"
                )
            }
        }

        // Post-process Debian/Termux JVM hierarchy:
        flattenJvmHierarchy(targetDir)
    }

    private fun flattenJvmHierarchy(targetDir: File) {
        // Termux packages extract to: data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/...
        // Elevate all files from the nested JVM directory directly into targetDir root so targetDir/bin/java is present.
        val candidateDir = targetDir.walkTopDown().firstOrNull { dir ->
            dir.isDirectory && dir.name.startsWith("java-") && File(dir, "bin/java").exists()
        } ?: targetDir.walkTopDown().firstOrNull { dir ->
            dir.isDirectory && dir != targetDir && File(dir, "bin/java").exists()
        }

        if (candidateDir != null && candidateDir != targetDir) {
            candidateDir.listFiles()?.forEach { child ->
                val dest = File(targetDir, child.name)
                if (dest.exists()) {
                    dest.deleteRecursively()
                }
                if (!child.renameTo(dest)) {
                    child.copyRecursively(dest, overwrite = true)
                    child.deleteRecursively()
                }
            }
            File(targetDir, "data").deleteRecursively()
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
               name == "llvm-objcopy" ||
               name == "llvm-ranlib" ||
               name == "llvm-readobj" ||
               name == "llvm-readelf" ||
               name == "ld.lld" ||
               name == "lld" ||
               name == "ld" ||
               name.endsWith("-ld") ||
               name.endsWith("-ld.lld") ||
               name == "busybox" ||
               name == "make" ||
               name == "sh" ||
               name == "yasm" ||
               name == "ar" ||
               name == "strip" ||
               name == "ranlib" ||
               parent == "bin"
    }
}
