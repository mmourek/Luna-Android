package cz.luna.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

class LunaService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var model: Model? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Luna – hlasová služba", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification("Připravuji český hlasový model…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            worker = Thread { runRecognition() }.also { it.start() }
        }
        return START_STICKY
    }

    private fun runRecognition() {
        try {
            val modelDir = File(filesDir, MODEL_DIR)
            if (!File(modelDir, "conf/model.conf").exists()) {
                updateNotification("Stahuji český model (46 MB)…")
                installModel(modelDir)
            }
            updateNotification("Načítám český model…")
            model = Model(modelDir.absolutePath)
            listenForWakeWord()
        } catch (e: Exception) {
            updateNotification("Chyba Luny: ${e.message ?: e.javaClass.simpleName}")
            running = false
        }
    }

    private fun installModel(modelDir: File) {
        val zipFile = File(cacheDir, "czech-model.zip")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("Stažení modelu: HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        zipFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        check(actualHash == MODEL_SHA256) { "Kontrola staženého modelu SHA-256 selhala" }

        val staging = File(filesDir, "model-installing")
        staging.deleteRecursively()
        staging.mkdirs()
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalized = entry.name.replace('\\', '/')
                val relative = normalized.substringAfter('/', "")
                if (relative.isNotEmpty()) {
                    val out = File(staging, relative)
                    check(out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Neplatná cesta v archivu" }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
            }
        }
        check(File(staging, "conf/model.conf").exists()) { "Archiv neobsahuje platný Vosk model" }
        modelDir.deleteRecursively()
        check(staging.renameTo(modelDir)) { "Nelze uložit český model" }
        zipFile.delete()
    }

    private fun listenForWakeWord() {
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "Mikrofon není dostupný (AudioRecord=$minBuffer)" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, 8192)
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Nepodařilo se inicializovat mikrofon" }
        audioRecord = recorder
        Recognizer(model, sampleRate.toFloat()).use { recognizer ->
            val buffer = ShortArray(4096)
            recorder.startRecording()
            updateNotification("Poslouchám – řekni „Ahoj Luno“")
            while (running && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0 && recognizer.acceptWaveForm(buffer, count)) {
                    val text = JSONObject(recognizer.result).optString("text")
                    if (isWakePhrase(text)) {
                        updateNotification("Slyším tě! Luna je připravena na příkaz.")
                        Thread.sleep(3500)
                        updateNotification("Poslouchám – řekni „Ahoj Luno“")
                        recognizer.reset()
                    }
                }
            }
        }
        recorder.stop()
        recorder.release()
        audioRecord = null
    }

    private fun isWakePhrase(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
            .replace('ů', 'u')
            .replace(Regex("[^a-zá-ž ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.contains("ahoj luno") || normalized.contains("ahoj luna")
    }

    private fun buildNotification(message: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("Luna")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Luna")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        if (::notificationManager.isInitialized) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(message))
        }
    }

    override fun onDestroy() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        audioRecord?.release()
        audioRecord = null
        worker?.interrupt()
        model?.close()
        model = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "luna_voice_service"
        private const val NOTIFICATION_ID = 1001
        private const val MODEL_DIR = "vosk-model-small-cs-0.4-rhasspy"
        private const val MODEL_URL = "https://huggingface.co/rhasspy/vosk-models/resolve/e7ac2109d134b5f2404ba95389b2fb51916d4cab/cs/vosk-model-small-cs-0.4-rhasspy.zip"
        private const val MODEL_SHA256 = "287c3bbefc8ad67b4ab9636eecef3d62acc3719990777d03e226db5a7f19fbda"
    }
}
