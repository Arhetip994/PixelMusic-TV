package com.unshoo.pixelmusic.tv.qr

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.jellyfin.JellyfinRepository
import com.unshoo.pixelmusic.data.listenbrainz.ListenBrainzRepository
import com.unshoo.pixelmusic.data.navidrome.NavidromeRepository
import com.unshoo.pixelmusic.data.network.youtube.InnerTubeParser
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

data class ReceivedTransferItem(
    val url: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val action: String = "play", // "play", "save_playlist", "search"
    val timestamp: Long = System.currentTimeMillis()
)

data class TvAccountStatus(
    val navidromeConnected: Boolean,
    val jellyfinConnected: Boolean,
    val listenBrainzConnected: Boolean
)

@Singleton
class TvQrTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val listenBrainzRepository: ListenBrainzRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val innerTubeParser: InnerTubeParser,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _relayTopic = MutableStateFlow("")
    val relayTopic: StateFlow<String> = _relayTopic.asStateFlow()

    private var relayJob: Job? = null

    private val _receivedItem = MutableStateFlow<ReceivedTransferItem?>(null)
    val receivedItem: StateFlow<ReceivedTransferItem?> = _receivedItem.asStateFlow()

    val isNavidromeConnected: StateFlow<Boolean> = navidromeRepository.isLoggedInFlow
    val isJellyfinConnected: StateFlow<Boolean> = jellyfinRepository.isLoggedInFlow
    val isListenBrainzConnected: StateFlow<Boolean> = listenBrainzRepository.accountState
        .map { it.isConnected }
        .stateIn(scope, SharingStarted.Eagerly, listenBrainzRepository.accountState.value.isConnected)

    private val _statusNotification = MutableStateFlow<String?>(null)
    val statusNotification: StateFlow<String?> = _statusNotification.asStateFlow()

    fun startServer(port: Int = 8088) {
        if (_pairingCode.value.isBlank()) {
            val code = (100_000..999_999).random().toString()
            val topic = "pixelmusic_tv_$code"
            _pairingCode.value = code
            _relayTopic.value = topic
            // Public zero-knowledge pairing relay URL (works on ANY device, mobile data, Wi-Fi, VPN on or off)
            _serverUrl.value = "https://ntfy.sh/$topic"
            startRelayListener(topic)
        }

        if (_isServerRunning.value && server != null) return

        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                routing {
                    get("/") {
                        call.respondText(generateWebHtml(), ContentType.Text.Html)
                    }

                    get("/api/status") {
                        val status = JsonObject().apply {
                            addProperty("tvName", "Pixel Music Android TV")
                            addProperty("navidromeConnected", navidromeRepository.isLoggedIn)
                            addProperty("jellyfinConnected", jellyfinRepository.isLoggedIn)
                            addProperty("listenBrainzConnected", listenBrainzRepository.accountState.value.isConnected)
                        }
                        call.respondText(status.toString(), ContentType.Application.Json)
                    }

                    post("/api/link") {
                        try {
                            val bodyText = call.receiveText()
                            handleIncomingPayload(bodyText)
                            val resp = JsonObject().apply {
                                addProperty("success", true)
                                addProperty("message", context.getString(R.string.tv_qr_msg_link_sent))
                            }
                            call.respondText(resp.toString(), ContentType.Application.Json)
                        } catch (e: Exception) {
                            Timber.e(e, "Error receiving link")
                            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                        }
                    }

                    post("/api/account") {
                        try {
                            val bodyText = call.receiveText()
                            handleIncomingPayload(bodyText)
                            val resp = JsonObject().apply {
                                addProperty("success", true)
                                addProperty("message", context.getString(R.string.tv_qr_msg_account_sent))
                            }
                            call.respondText(resp.toString(), ContentType.Application.Json)
                        } catch (e: Exception) {
                            Timber.e(e, "Error connecting account")
                            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                        }
                    }
                }
            }.start(wait = false)
            _isServerRunning.value = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start local Ktor server")
        }
    }

    private fun startRelayListener(topic: String) {
        relayJob?.cancel()
        val streamingClient = okHttpClient.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        relayJob = scope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("https://ntfy.sh/$topic/raw")
                        .build()
                    streamingClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            delay(3000)
                            return@use
                        }
                        val source = response.body?.source() ?: return@use
                        while (!source.exhausted() && isActive) {
                            val line = source.readUtf8Line()?.trim() ?: continue
                            if (line.isNotEmpty()) {
                                handleIncomingPayload(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Relay listener reconnecting...")
                    delay(3000)
                }
            }
        }
    }

    fun handleIncomingPayload(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return

        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JsonParser.parseString(trimmed).asJsonObject
                if (json.has("service")) {
                    handleAccountJson(json)
                    return
                } else if (json.has("url")) {
                    val url = json.get("url")?.asString.orEmpty().trim()
                    val title = json.get("title")?.asString.orEmpty().trim()
                    val action = json.get("action")?.asString ?: "play"
                    processLink(url, title, action)
                    return
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error parsing incoming JSON, handling as plain URL/text")
        }

        processLink(trimmed, "", "play")
    }

    private fun processLink(url: String, title: String, action: String) {
        if (url.isBlank()) return
        val ytPlaylistId = innerTubeParser.extractPlaylistId(url)
        val ytVideoId = innerTubeParser.extractVideoId(url)

        if (ytPlaylistId != null) {
            _statusNotification.value = context.getString(R.string.tv_qr_status_loading_playlist)
            scope.launch {
                val parsed = innerTubeParser.parsePlaylist(url, region = "US")
                if (parsed != null && parsed.tracks.isNotEmpty()) {
                    val finalTitle = if (title.isNotBlank()) title else parsed.title
                    playlistPreferencesRepository.createPlaylist(name = finalTitle, songIds = emptyList())
                    _statusNotification.value = context.getString(R.string.tv_qr_status_imported_playlist, finalTitle, parsed.tracks.size)
                    _receivedItem.value = ReceivedTransferItem(
                        url = url,
                        title = "$finalTitle (${parsed.tracks.size})",
                        thumbnailUrl = parsed.tracks.firstOrNull()?.thumbnailUrl,
                        action = action
                    )
                } else {
                    _statusNotification.value = context.getString(R.string.tv_qr_status_failed_playlist)
                }
            }
        } else if (ytVideoId != null) {
            _statusNotification.value = context.getString(R.string.tv_qr_status_loading_track)
            scope.launch {
                val track = innerTubeParser.parseSong(ytVideoId, region = "US")
                val artist = track?.artist.orEmpty()
                val songTitle = track?.title ?: (if (title.isNotBlank()) title else "YouTube Track")
                val trackTitle = if (artist.isNotBlank()) "$artist — $songTitle" else songTitle
                _statusNotification.value = "✅ $trackTitle"
                _receivedItem.value = ReceivedTransferItem(
                    url = url,
                    title = trackTitle,
                    artist = artist,
                    thumbnailUrl = track?.thumbnailUrl,
                    action = action
                )
            }
        } else if (action == "save_playlist") {
            scope.launch {
                val defaultPlaylistName = context.getString(R.string.tv_playlist_import_default, (System.currentTimeMillis() % 10000).toInt())
                val playlistName = if (title.isNotBlank()) title else defaultPlaylistName
                playlistPreferencesRepository.createPlaylist(playlistName, emptyList())
            }
            val item = ReceivedTransferItem(url = url, title = title, action = action)
            _receivedItem.value = item
            _statusNotification.value = context.getString(R.string.tv_qr_status_received_link, if (title.isNotBlank()) title else url)
        } else {
            val item = ReceivedTransferItem(url = url, title = title, action = action)
            _receivedItem.value = item
            _statusNotification.value = context.getString(R.string.tv_qr_status_received_link, if (title.isNotBlank()) title else url)
        }
    }

    private fun handleAccountJson(json: JsonObject) {
        val service = json.get("service")?.asString.orEmpty().lowercase()
        val url = json.get("url")?.asString.orEmpty().trim()
        val username = json.get("username")?.asString.orEmpty().trim()
        val password = json.get("password")?.asString.orEmpty().trim()
        val token = json.get("token")?.asString.orEmpty().trim()

        scope.launch {
            when (service) {
                "navidrome", "subsonic" -> {
                    val res = navidromeRepository.login(url, username, password)
                    if (res.isSuccess) {
                        _statusNotification.value = context.getString(R.string.tv_qr_status_connected_service, "Navidrome")
                    }
                }
                "jellyfin" -> {
                    val res = jellyfinRepository.login(url, username, password)
                    if (res.isSuccess) {
                        _statusNotification.value = context.getString(R.string.tv_qr_status_connected_service, "Jellyfin")
                    }
                }
                "listenbrainz" -> {
                    val res = listenBrainzRepository.connect(token, url.ifBlank { null })
                    if (res.isSuccess) {
                        _statusNotification.value = context.getString(R.string.tv_qr_status_connected_service, "ListenBrainz")
                    }
                }
            }
        }
    }

    fun stopServer() {
        try {
            server?.stop(500, 1000)
            server = null
            _isServerRunning.value = false
        } catch (e: Exception) {
            Timber.e(e, "Error stopping server")
        }
    }

    fun clearReceivedItem() {
        _receivedItem.value = null
    }

    fun clearNotification() {
        _statusNotification.value = null
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) {
                            candidates.add(host)
                        }
                    }
                }
            }
            val localIp = candidates.firstOrNull { it.startsWith("192.168.") }
                ?: candidates.firstOrNull { it.startsWith("10.") && !it.startsWith("10.0.2.") }
                ?: candidates.firstOrNull { it.startsWith("172.") }
                ?: candidates.firstOrNull()

            // If we are on Android TV Emulator (10.0.2.x), use the Host PC Wi-Fi IP so phones on the same Wi-Fi can connect!
            if (localIp == null || localIp.startsWith("10.0.2.")) {
                return "192.168.0.38"
            }
            return localIp
        } catch (e: Exception) {
            Timber.e(e, "Error finding local IP")
        }
        return "192.168.0.38"
    }

    fun setServerHost(host: String, port: Int = 8088) {
        _serverUrl.value = "http://$host:$port"
    }

    private fun generateWebHtml(): String {
        return """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Pixel Music TV Connect</title>
<style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
        background-color: #111318;
        color: #e2e2e9;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        padding: 20px;
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
    }
    .container {
        max-width: 480px;
        width: 100%;
    }
    .header {
        text-align: center;
        margin-bottom: 24px;
    }
    .header h1 {
        font-size: 24px;
        font-weight: 700;
        color: #a8c7fa;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
    }
    .badge {
        display: inline-block;
        padding: 4px 12px;
        border-radius: 12px;
        background-color: #1e2530;
        color: #6dd58c;
        font-size: 13px;
        font-weight: 600;
        margin-top: 8px;
    }
    .tabs {
        display: flex;
        gap: 8px;
        background-color: #1c1f26;
        padding: 6px;
        border-radius: 16px;
        margin-bottom: 20px;
    }
    .tab-btn {
        flex: 1;
        padding: 10px 14px;
        border: none;
        background: transparent;
        color: #c4c6d0;
        font-size: 14px;
        font-weight: 600;
        border-radius: 12px;
        cursor: pointer;
        transition: all 0.2s;
    }
    .tab-btn.active {
        background-color: #a8c7fa;
        color: #042f65;
    }
    .card {
        background-color: #1c1f26;
        border-radius: 20px;
        padding: 20px;
        margin-bottom: 20px;
        border: 1px solid #2d313b;
    }
    .form-group {
        margin-bottom: 16px;
    }
    label {
        display: block;
        font-size: 13px;
        font-weight: 600;
        color: #a8c7fa;
        margin-bottom: 6px;
    }
    input, textarea, select {
        width: 100%;
        background-color: #111318;
        border: 1px solid #3b404d;
        border-radius: 12px;
        padding: 14px;
        color: #e2e2e9;
        font-size: 15px;
        outline: none;
        transition: border 0.2s;
    }
    input:focus, textarea:focus, select:focus {
        border-color: #a8c7fa;
    }
    .btn {
        display: block;
        width: 100%;
        padding: 14px;
        border-radius: 14px;
        border: none;
        font-size: 15px;
        font-weight: 700;
        cursor: pointer;
        margin-top: 10px;
        transition: opacity 0.2s;
    }
    .btn-primary {
        background-color: #a8c7fa;
        color: #042f65;
    }
    .btn-secondary {
        background-color: #2b313d;
        color: #c4e7ff;
    }
    .btn:active {
        opacity: 0.8;
    }
    .status-box {
        display: none;
        padding: 14px;
        border-radius: 12px;
        margin-top: 16px;
        font-size: 14px;
        font-weight: 600;
        text-align: center;
    }
    .status-box.success {
        display: block;
        background-color: rgba(109, 213, 140, 0.15);
        color: #6dd58c;
        border: 1px solid #6dd58c;
    }
    .status-box.error {
        display: block;
        background-color: rgba(255, 180, 171, 0.15);
        color: #ffb4ab;
        border: 1px solid #ffb4ab;
    }
    .service-selector {
        display: flex;
        gap: 6px;
        margin-bottom: 16px;
    }
    .srv-btn {
        flex: 1;
        padding: 8px 4px;
        font-size: 12px;
        font-weight: 600;
        border-radius: 10px;
        border: 1px solid #3b404d;
        background-color: #111318;
        color: #c4c6d0;
        cursor: pointer;
    }
    .srv-btn.selected {
        background-color: #2d3d54;
        border-color: #a8c7fa;
        color: #a8c7fa;
    }
</style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>🎵 Pixel Music TV</h1>
        <div class="badge" id="badge-text">● TV connected</div>
    </div>

    <div class="tabs">
        <button class="tab-btn active" id="tab-link-btn" onclick="switchTab('link')">🔗 Send Link</button>
        <button class="tab-btn" id="tab-acc-btn" onclick="switchTab('accounts')">🔑 Accounts</button>
    </div>

    <!-- TAB 1: LINKS & PLAYLISTS -->
    <div id="tab-link" class="card">
        <div class="form-group">
            <label id="lbl-link-url">Track or Playlist Link</label>
            <input type="text" id="linkUrl" placeholder="https://music.youtube.com/... or URL" />
        </div>
        <div class="form-group">
            <label id="lbl-link-title">Playlist Title (optional)</label>
            <input type="text" id="linkTitle" placeholder="Favorite Mix" />
        </div>
        <button class="btn btn-primary" id="btn-play" onclick="sendLink('play')">▶ Play on TV</button>
        <button class="btn btn-secondary" id="btn-save" onclick="sendLink('save_playlist')">➕ Save to Playlists</button>
        <div id="linkStatus" class="status-box"></div>
    </div>

    <!-- TAB 2: ACCOUNTS LOGIN -->
    <div id="tab-accounts" class="card" style="display:none;">
        <label id="lbl-select-service">Select service:</label>
        <div class="service-selector">
            <button class="srv-btn selected" id="btn-navidrome" onclick="selectService('navidrome')">Navidrome</button>
            <button class="srv-btn" id="btn-jellyfin" onclick="selectService('jellyfin')">Jellyfin</button>
            <button class="srv-btn" id="btn-listenbrainz" onclick="selectService('listenbrainz')">ListenBrainz</button>
        </div>

        <div id="credentials-fields">
            <div class="form-group" id="group-url">
                <label id="lbl-url">Server Address (URL)</label>
                <input type="url" id="accUrl" placeholder="https://music.my-server.com" />
            </div>
            <div class="form-group" id="group-username">
                <label id="lbl-username">Username</label>
                <input type="text" id="accUsername" placeholder="Username" />
            </div>
            <div class="form-group" id="group-password">
                <label id="lbl-password">Password</label>
                <input type="password" id="accPassword" placeholder="••••••••" />
            </div>
        </div>

        <button class="btn btn-primary" id="btn-connect" onclick="connectAccount()">🚀 Connect on TV</button>
        <div id="accStatus" class="status-box"></div>
    </div>
</div>

<script>
    let currentService = 'navidrome';
    const isRu = (navigator.language || navigator.userLanguage || '').toLowerCase().startsWith('ru');

    if (isRu) {
        document.getElementById('badge-text').textContent = '● ТВ подключен к Wi-Fi';
        document.getElementById('tab-link-btn').textContent = '🔗 Передать ссылку';
        document.getElementById('tab-acc-btn').textContent = '🔑 Вход в аккаунты';
        document.getElementById('lbl-link-url').textContent = 'Ссылка на плейлист или трек';
        document.getElementById('linkUrl').placeholder = 'https://music.youtube.com/... или URL';
        document.getElementById('lbl-link-title').textContent = 'Название плейлиста (необязательно)';
        document.getElementById('linkTitle').placeholder = 'Любимый микс';
        document.getElementById('btn-play').textContent = '▶ Воспроизвести на ТВ';
        document.getElementById('btn-save').textContent = '➕ Сохранить в плейлисты на ТВ';
        document.getElementById('lbl-select-service').textContent = 'Выберите сервис:';
        document.getElementById('lbl-url').textContent = 'Адрес сервера (URL)';
        document.getElementById('lbl-username').textContent = 'Имя пользователя';
        document.getElementById('lbl-password').textContent = 'Пароль';
        document.getElementById('btn-connect').textContent = '🚀 Подключить на ТВ';
    }

    function switchTab(tab) {
        document.getElementById('tab-link').style.display = tab === 'link' ? 'block' : 'none';
        document.getElementById('tab-accounts').style.display = tab === 'accounts' ? 'block' : 'none';
        const btns = document.querySelectorAll('.tab-btn');
        btns[0].classList.toggle('active', tab === 'link');
        btns[1].classList.toggle('active', tab === 'accounts');
    }

    function selectService(service) {
        currentService = service;
        document.getElementById('btn-navidrome').classList.toggle('selected', service === 'navidrome');
        document.getElementById('btn-jellyfin').classList.toggle('selected', service === 'jellyfin');
        document.getElementById('btn-listenbrainz').classList.toggle('selected', service === 'listenbrainz');

        const groupUrl = document.getElementById('group-url');
        const groupUsername = document.getElementById('group-username');
        const groupPassword = document.getElementById('group-password');
        const lblUrl = document.getElementById('lbl-url');
        const lblPassword = document.getElementById('lbl-password');
        const accUrl = document.getElementById('accUrl');

        if (service === 'listenbrainz') {
            lblUrl.textContent = isRu ? 'Адрес сервера (необязательно)' : 'Server URL (optional)';
            accUrl.placeholder = 'https://api.listenbrainz.org';
            groupUsername.style.display = 'none';
            lblPassword.textContent = 'User API Token';
            document.getElementById('accPassword').placeholder = isRu ? 'Токен из профиля ListenBrainz' : 'Token from ListenBrainz profile';
        } else {
            lblUrl.textContent = isRu ? 'Адрес сервера (URL)' : 'Server Address (URL)';
            accUrl.placeholder = service === 'navidrome' ? 'https://music.navidrome.org' : 'https://jellyfin.local:8096';
            groupUsername.style.display = 'block';
            lblPassword.textContent = isRu ? 'Пароль' : 'Password';
            document.getElementById('accPassword').placeholder = '••••••••';
        }
    }

    async function sendLink(action) {
        const url = document.getElementById('linkUrl').value.trim();
        const title = document.getElementById('linkTitle').value.trim();
        const statusBox = document.getElementById('linkStatus');
        if (!url) {
            statusBox.className = 'status-box error';
            statusBox.textContent = isRu ? 'Введите ссылку или название трека!' : 'Please enter track URL or title!';
            return;
        }

        statusBox.className = 'status-box';
        statusBox.style.display = 'block';
        statusBox.textContent = isRu ? 'Отправка на ТВ...' : 'Sending to TV...';

        try {
            const resp = await fetch('/api/link', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url, title: title, action: action })
            });
            const data = await resp.json();
            if (data.success) {
                statusBox.className = 'status-box success';
                statusBox.textContent = '✅ ' + data.message;
                document.getElementById('linkUrl').value = '';
                document.getElementById('linkTitle').value = '';
            } else {
                statusBox.className = 'status-box error';
                statusBox.textContent = '❌ ' + (data.message || (isRu ? 'Ошибка отправки' : 'Send error'));
            }
        } catch (e) {
            statusBox.className = 'status-box error';
            statusBox.textContent = '❌ ' + (isRu ? 'Ошибка сети: ' : 'Network error: ') + e.message;
        }
    }

    async function connectAccount() {
        const url = document.getElementById('accUrl').value.trim();
        const username = document.getElementById('accUsername').value.trim();
        const password = document.getElementById('accPassword').value.trim();
        const statusBox = document.getElementById('accStatus');

        if (currentService !== 'listenbrainz' && (!url || !username || !password)) {
            statusBox.className = 'status-box error';
            statusBox.textContent = isRu ? 'Заполните адрес сервера, логин и пароль!' : 'Please enter server URL, username and password!';
            return;
        }
        if (currentService === 'listenbrainz' && !password) {
            statusBox.className = 'status-box error';
            statusBox.textContent = isRu ? 'Введите API Token!' : 'Please enter API Token!';
            return;
        }

        statusBox.className = 'status-box';
        statusBox.style.display = 'block';
        statusBox.textContent = isRu ? 'Подключение на ТВ...' : 'Connecting on TV...';

        try {
            const payload = {
                service: currentService,
                url: url,
                username: username,
                password: password,
                token: password
            };
            const resp = await fetch('/api/account', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (data.success) {
                statusBox.className = 'status-box success';
                statusBox.textContent = '✅ ' + data.message;
            } else {
                statusBox.className = 'status-box error';
                statusBox.textContent = '❌ ' + (data.message || (isRu ? 'Ошибка подключения' : 'Connection error'));
            }
        } catch (e) {
            statusBox.className = 'status-box error';
            statusBox.textContent = '❌ ' + (isRu ? 'Ошибка связи с ТВ: ' : 'TV connection error: ') + e.message;
        }
    }
</script>
</body>
</html>
        """.trimIndent()
    }
}
