package io.github.loshine.andpub.platform

import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ToolStatus
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun pickArtifactFilePath(): String? = withContext(Dispatchers.Default) {
    val chooser = JFileChooser()
    chooser.dialogTitle = "选择 APK 或 AAB"
    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

actual suspend fun inspectToolSettings(
    androidSdkPath: String,
    bundletoolPath: String,
): ToolStatus = withContext(Dispatchers.IO) {
    val aapt2 = findAapt2(androidSdkPath)
    val bundletool = bundletoolPath.trim().takeIf { it.isNotEmpty() }?.let(::File)
    val bundletoolUsable = bundletool?.isFile == true
    val messages = buildList {
        if (aapt2 == null) {
            add("未找到 aapt2；请设置 Android SDK 路径或配置 ANDROID_HOME / ANDROID_SDK_ROOT")
        } else {
            add("aapt2：${aapt2.absolutePath}")
        }
        if (bundletoolPath.isBlank()) {
            add("未设置 bundletool-all.jar；AAB manifest 解析不可用")
        } else if (!bundletoolUsable) {
            add("bundletool-all.jar 路径无效")
        } else {
            add("bundletool：${bundletool.absolutePath}")
        }
    }
    ToolStatus(
        aapt2Path = aapt2?.absolutePath,
        bundletoolUsable = bundletoolUsable,
        messages = messages,
    )
}

actual suspend fun inspectLocalArtifact(
    path: String,
    androidSdkPath: String,
    bundletoolPath: String,
): Result<ArtifactInspection> =
    runCatching {
        withContext(Dispatchers.IO) {
            val file = File(path)
            require(file.exists()) { "文件不存在" }
            require(file.isFile) { "请选择文件" }

            val metadata = when (file.extension.lowercase()) {
                "apk" -> inspectApk(file, androidSdkPath)
                "aab" -> inspectAab(file, bundletoolPath)
                else -> ArtifactMetadata(
                    warnings = listOf("仅支持 APK/AAB，当前文件类型无法解析 manifest"),
                )
            }

            ArtifactInspection(
                fileName = file.name,
                fileSizeBytes = file.length(),
                md5 = file.digest("MD5"),
                sha1 = file.digest("SHA-1"),
                sha256 = file.digest("SHA-256"),
                packageName = metadata.packageName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                abiList = metadata.abiList.ifEmpty { file.readAbiList() },
                warnings = metadata.warnings,
            )
        }
    }

actual suspend fun downloadArtifactFromUrl(url: String): Result<String> =
    runCatching {
        withContext(Dispatchers.IO) {
            val uri = URI(url)
            require(uri.scheme == "http" || uri.scheme == "https") { "URL 仅支持 http/https" }
            val target = createDownloadedArtifactFile(uri)
            val connection = uri.toURL().openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 120_000
            }
            if (connection is HttpURLConnection) {
                connection.instanceFollowRedirects = true
                require(connection.responseCode in 200..299) {
                    "下载失败，HTTP ${connection.responseCode}"
                }
            }
            connection.getInputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            require(target.length() > 0) { "下载到的文件为空" }
            target.absolutePath
        }
    }

private data class ArtifactMetadata(
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val abiList: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

private fun inspectApk(
    file: File,
    configuredSdkPath: String,
): ArtifactMetadata {
    val aapt2 = findAapt2(configuredSdkPath)
        ?: return ArtifactMetadata(
            abiList = file.readAbiList(),
            warnings = listOf("未找到 Android SDK build-tools/aapt2，无法解析包名和版本"),
        )

    val output = runCommand(listOf(aapt2.absolutePath, "dump", "badging", file.absolutePath))
    val packageLine = output.lineSequence().firstOrNull { it.startsWith("package:") }.orEmpty()
    val nativeCodeLine = output.lineSequence().firstOrNull { it.startsWith("native-code:") }.orEmpty()
    return ArtifactMetadata(
        packageName = packageLine.singleQuotedValue("name"),
        versionName = packageLine.singleQuotedValue("versionName"),
        versionCode = packageLine.singleQuotedValue("versionCode")?.toLongOrNull(),
        abiList = nativeCodeLine.singleQuotedValues(),
    )
}

private fun inspectAab(
    file: File,
    bundletoolPath: String,
): ArtifactMetadata {
    val bundletool = bundletoolPath.trim().takeIf { it.isNotEmpty() }?.let(::File)
    if (bundletool == null || !bundletool.isFile) {
        return ArtifactMetadata(
            warnings = listOf("未配置 bundletool-all.jar，无法解析 AAB manifest"),
        )
    }

    val output = runCommand(
        listOf(
            "java",
            "-jar",
            bundletool.absolutePath,
            "dump",
            "manifest",
            "--bundle=${file.absolutePath}",
        )
    )
    return ArtifactMetadata(
        packageName = output.xmlAttribute("package"),
        versionName = output.xmlAttribute("versionName") ?: output.xmlAttribute("android:versionName"),
        versionCode = (
            output.xmlAttribute("versionCode") ?: output.xmlAttribute("android:versionCode")
            )?.toLongOrNull(),
        abiList = file.readBundleAbiList(),
    )
}

private fun findAapt2(configuredSdkPath: String): File? {
    val sdkCandidates = buildList {
        configuredSdkPath.trim().takeIf { it.isNotEmpty() }?.let { add(File(it)) }
        env("ANDROID_HOME")?.let { add(File(it)) }
        env("ANDROID_SDK_ROOT")?.let { add(File(it)) }
        add(File(System.getProperty("user.home"), "Library/Android/sdk"))
    }.distinctBy { it.absolutePath }

    return sdkCandidates.asSequence()
        .filter { it.isDirectory }
        .mapNotNull { sdk ->
            val buildTools = File(sdk, "build-tools")
            buildTools.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.map { File(it, executableName("aapt2")) }
                ?.firstOrNull { it.isFile && it.canExecute() }
        }
        .firstOrNull()
}

private fun createDownloadedArtifactFile(uri: URI): File {
    val path = uri.path.orEmpty()
    val suffix = when {
        path.endsWith(".apk", ignoreCase = true) -> ".apk"
        path.endsWith(".aab", ignoreCase = true) -> ".aab"
        else -> ".bin"
    }
    val dir = File(System.getProperty("java.io.tmpdir"), "andpub-url-artifacts").apply {
        mkdirs()
    }
    return File.createTempFile("artifact-", suffix, dir)
}

private fun runCommand(command: List<String>): String {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    require(exitCode == 0) {
        "命令执行失败：${command.joinToString(" ")}\n$output"
    }
    return output
}

private fun File.digest(algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun File.readAbiList(): List<String> {
    if (extension.lowercase() != "apk") return emptyList()

    return runCatching {
        ZipFile(this).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.count { char -> char == '/' } >= 2 }
                .map { it.removePrefix("lib/").substringBefore("/") }
                .distinct()
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())
}

private fun File.readBundleAbiList(): List<String> =
    runCatching {
        ZipFile(this).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("base/lib/") && it.count { char -> char == '/' } >= 3 }
                .map { it.removePrefix("base/lib/").substringBefore("/") }
                .distinct()
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())

private fun String.singleQuotedValue(name: String): String? =
    Regex("\\b${Regex.escape(name)}='([^']*)'").find(this)?.groupValues?.getOrNull(1)

private fun String.singleQuotedValues(): List<String> =
    Regex("'([^']*)'").findAll(this).map { it.groupValues[1] }.toList()

private fun String.xmlAttribute(name: String): String? =
    Regex("\\b${Regex.escape(name)}=\"([^\"]*)\"").find(this)?.groupValues?.getOrNull(1)

private fun env(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }

private fun executableName(name: String): String =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "$name.exe"
    } else {
        name
    }
