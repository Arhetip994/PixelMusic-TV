package com.unshoo.pixelmusic.tv

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.unshoo.pixelmusic.data.model.LyricsSourcePreference
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.service.MusicService
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.MainViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.PixelPlayerTheme
import com.unshoo.pixelmusic.utils.AppLocaleManager
import com.unshoo.pixelmusic.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var tvQrTransferManager: com.unshoo.pixelmusic.tv.qr.TvQrTransferManager

    @Inject
    lateinit var innerTubeParser: com.unshoo.pixelmusic.data.network.youtube.InnerTubeParser

    private val playerViewModel: PlayerViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val playlistViewModel: com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel by viewModels()
    private val equalizerViewModel: com.unshoo.pixelmusic.presentation.viewmodel.EqualizerViewModel by viewModels()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        lifecycleScope.launch {
            userPreferencesRepository.setInitialSetupDone(true)
            if (isGranted) {
                mainViewModel.retrySync()
            }
        }
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_locale_preferences", Context.MODE_PRIVATE)
        if (!prefs.contains("app_language_tag")) {
            prefs.edit().putString("app_language_tag", "en").apply()
        }
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_locale_preferences", Context.MODE_PRIVATE)
        if (!prefs.contains("app_language_tag")) {
            AppLocaleManager.applyLanguage(this, "en")
        }
        super.onCreate(savedInstanceState)
        LogUtils.d(this, "TvMainActivity onCreate")

        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        lifecycleScope.launch {
            userPreferencesRepository.setInitialSetupDone(true)
            userPreferencesRepository.setExternalLyricsEnabled(true)
            userPreferencesRepository.setLyricsSourcePreference(LyricsSourcePreference.API_FIRST)
            if (ContextCompat.checkSelfPermission(this@TvMainActivity, perm) == PackageManager.PERMISSION_GRANTED) {
                mainViewModel.retrySync()
            } else {
                permissionLauncher.launch(perm)
            }
        }

        // Keep screen awake while music is playing on TV
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.stablePlayerState
                    .map { it.isPlaying }
                    .distinctUntilChanged()
                    .collect { isPlaying ->
                        if (isPlaying) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }

        setContent {
            // TV UI is always in high-contrast dark theme to prevent OLED burn-in and room glare
            PixelPlayerTheme(darkTheme = true) {
                TvMainScreen(
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    mainViewModel = mainViewModel,
                    playlistViewModel = playlistViewModel,
                    equalizerViewModel = equalizerViewModel,
                    musicRepository = musicRepository,
                    tvQrTransferManager = tvQrTransferManager,
                    innerTubeParser = innerTubeParser,
                    userPreferencesRepository = userPreferencesRepository
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "TvMainActivity onStart")
        playerViewModel.onMainActivityStart()
        tvQrTransferManager.startServer()

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({}, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "TvMainActivity onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    /**
     * Intercept hardware media keys from TV remote control.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_ADD -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_M -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                playerViewModel.playPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playerViewModel.nextSong()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playerViewModel.previousSong()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                val currentPos = playerViewModel.currentPlaybackPosition.value
                playerViewModel.seekTo(currentPos + 10_000L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                val currentPos = playerViewModel.currentPlaybackPosition.value
                playerViewModel.seekTo((currentPos - 10_000L).coerceAtLeast(0L))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
