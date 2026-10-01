package com.eltnegcellist.emma.tts

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Kitten TTS assets for the GPL-free Android path.
 *
 * Installs only the official Kitten ONNX model, voice embeddings, and CMUDict.
 * The phonemizer is Mitsukotoba's CMUDict-based implementation.
 */
object KittenModelStore {
    const val MODEL_NAME = "kitten-nano-en-v0_8-fp32-cmudict-v1"
    const val APPROX_DOWNLOAD_MB = 64

    private const val KITTEN_REVISION = "87b12ff7859cdebd9c055c987a586101fad5b650"
    private const val CMUDICT_REVISION = "74790861f652b15e4ac49015a90074ad62a27690"

    const val MODEL_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/" +
            KITTEN_REVISION + "/kitten_tts_nano_v0_8.onnx"
    const val VOICES_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/" +
            KITTEN_REVISION + "/voices.npz"
    const val CMUDICT_URL =
        "https://raw.githubusercontent.com/cmusphinx/cmudict/" +
            CMUDICT_REVISION + "/cmudict.dict"

    const val MODEL_SHA256 =
        "320564d2615f235de972ca27a7f39551c94185cfa24ca85b07a29084135f1e5e"
    const val VOICES_SHA256 =
        "8aa7cee235abb0739cb51e6559685f65a4dacd95568833d05699b1633f519b3f"
    const val CMUDICT_SHA256 =
        "81917843c7f44ce2b094ac63873c2c7a4cf802040792c455ba3ca406891c3d22"

    private const val MODEL_MIN_BYTES = 50_000_000L
    private const val VOICES_MIN_BYTES = 3_000_000L
    private const val CMUDICT_MIN_BYTES = 3_500_000L

    fun directory(context: Context): File =
        File(context.filesDir, "tts/" + MODEL_NAME)

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val downloadDir = File(context.filesDir, "model-downloads/" + MODEL_NAME)
        downloadDir.mkdirs()

        val modelDownload = File(downloadDir, "model.onnx")
        val voicesDownload = File(downloadDir, "voices.npz")
        val cmuDownload = File(downloadDir, "cmudict.dict")

        InAppModelDownloader.download(
            url = MODEL_URL,
            destination = modelDownload,
            minimumBytes = MODEL_MIN_BYTES,
            freeSpaceMarginBytes = 256L * 1024L * 1024L,
        ) { state ->
            progress(state.percent?.let { (it * 88) / 100 })
        }.getOrThrow()
        verifySha256(modelDownload, MODEL_SHA256, "Kitten TTSモデル")

        InAppModelDownloader.download(
            url = VOICES_URL,
            destination = voicesDownload,
            minimumBytes = VOICES_MIN_BYTES,
            freeSpaceMarginBytes = 128L * 1024L * 1024L,
        ) { state ->
            progress(state.percent?.let { 88 + (it * 6) / 100 })
        }.getOrThrow()
        verifySha256(voicesDownload, VOICES_SHA256, "Kitten Kiki音声")

        InAppModelDownloader.download(
            url = CMUDICT_URL,
            destination = cmuDownload,
            minimumBytes = CMUDICT_MIN_BYTES,
            freeSpaceMarginBytes = 96L * 1024L * 1024L,
        ) { state ->
            progress(state.percent?.let { 94 + (it * 6) / 100 })
        }.getOrThrow()
        verifySha256(cmuDownload, CMUDICT_SHA256, "CMU Pronouncing Dictionary")

        installVerifiedFiles(context, modelDownload, voicesDownload, cmuDownload)
        progress(100)
    }

    private fun installVerifiedFiles(
        context: Context,
        model: File,
        voices: File,
        dictionary: File,
    ) {
        val target = directory(context)
        val staging = File(context.filesDir, "tts/" + MODEL_NAME + ".part")
        val backup = File(context.filesDir, "tts/" + MODEL_NAME + ".old")

        staging.deleteRecursively()
        require(staging.mkdirs()) { "Kitten TTSの一時フォルダを作成できませんでした。" }

        try {
            model.copyTo(File(staging, "model.onnx"), overwrite = true)
            voices.copyTo(File(staging, "voices.npz"), overwrite = true)
            dictionary.copyTo(File(staging, "cmudict.dict"), overwrite = true)

            require(isInstalledAt(staging)) {
                "非GPL版Kitten TTSに必要なファイルが不足しています。"
            }

            // Parse the Kiki entry and dictionary before replacing the working install.
            KittenVoiceLoader.loadKiki(File(staging, "voices.npz"))
            KittenPhonemizer.fromFile(File(staging, "cmudict.dict"))

            backup.deleteRecursively()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.renameTo(backup)) {
                error("旧Kitten TTSモデルを退避できませんでした。")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("Kitten TTSモデルを配置できませんでした。")
            }
            backup.deleteRecursively()

            // Remove the old sherpa/eSpeak model only after the GPL-free install is valid.
            File(context.filesDir, "tts/kitten-nano-en-v0_8-fp32").deleteRecursively()
            File(context.filesDir, "tts/kitten-nano-en-v0_8-int8").deleteRecursively()

            model.delete()
            voices.delete()
            dictionary.delete()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isInstalledAt(dir: File): Boolean {
        val model = File(dir, "model.onnx")
        val voices = File(dir, "voices.npz")
        val dictionary = File(dir, "cmudict.dict")
        return model.isFile && model.length() >= MODEL_MIN_BYTES &&
            voices.isFile && voices.length() >= VOICES_MIN_BYTES &&
            dictionary.isFile && dictionary.length() >= CMUDICT_MIN_BYTES
    }

    private fun verifySha256(file: File, expected: String, label: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (input.read(buffer) >= 0) Unit
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) {
            label + "のSHA-256が一致しません。"
        }
    }
}
