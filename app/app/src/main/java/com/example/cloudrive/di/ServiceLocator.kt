package com.example.cloudrive.di

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.example.cloudrive.data.local.TokenStore
import com.example.cloudrive.data.local.ViewModePrefs
import com.example.cloudrive.data.local.music.MusicDatabase
import com.example.cloudrive.data.local.music.MusicPrefs
import com.example.cloudrive.data.remote.AuthInterceptor
import com.example.cloudrive.data.remote.LanResolver
import com.example.cloudrive.data.remote.TokenAuthenticator
import com.example.cloudrive.data.remote.api.AuthApi
import com.example.cloudrive.data.remote.api.FileApi
import com.example.cloudrive.data.remote.api.FolderApi
import com.example.cloudrive.data.remote.api.MusicApi
import com.example.cloudrive.data.remote.api.TrashApi
import com.example.cloudrive.data.remote.api.UploadApi
import com.example.cloudrive.data.repository.AuthRepository
import com.example.cloudrive.data.repository.DownloadRepository
import com.example.cloudrive.data.repository.FileRepository
import com.example.cloudrive.data.repository.FolderRepository
import com.example.cloudrive.data.repository.PlaylistRepository
import com.example.cloudrive.data.repository.SaveToDeviceRepository
import com.example.cloudrive.data.repository.TrackRepository
import com.example.cloudrive.data.repository.TrashRepository
import com.example.cloudrive.data.repository.UploadRepository
import com.example.cloudrive.playback.NetworkMonitor
import com.example.cloudrive.playback.PlayerController
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ServiceLocator(private val app: Application) {

    val tokenStore: TokenStore by lazy { TokenStore(app) }
    val viewModePrefs: ViewModePrefs by lazy { ViewModePrefs(app) }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Bare client used exclusively by TokenAuthenticator for refresh calls
    private val bareClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // Short-timeout client for LAN discovery probes (GET /network, GET /ping) —
    // these must fail fast, not hang for the app's normal 30s connect timeout.
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()
    }

    val lanResolver: LanResolver by lazy { LanResolver(probeClient, tokenStore) }

    private var _authenticatedClient: OkHttpClient? = null
    val authenticatedClient: OkHttpClient get() {
        if (_authenticatedClient == null) rebuildClient()
        return _authenticatedClient!!
    }

    private var _retrofit: Retrofit? = null
    private val retrofit: Retrofit get() {
        if (_retrofit == null) rebuildClient()
        return _retrofit!!
    }

    @Synchronized
    fun rebuildClient() {
        lanResolver.invalidate()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, bareClient))
            .addInterceptor(loggingInterceptor)
            .build()
        _authenticatedClient = client
        _retrofit = Retrofit.Builder()
            .baseUrl(tokenStore.serverUrl + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        // Invalidate API instances
        _authApi = null
        _fileApi = null
        _folderApi = null
        _trashApi = null
        _uploadApi = null
        _musicApi = null
    }

    private var _authApi: AuthApi? = null
    val authApi: AuthApi get() = _authApi ?: retrofit.create(AuthApi::class.java).also { _authApi = it }

    private var _fileApi: FileApi? = null
    val fileApi: FileApi get() = _fileApi ?: retrofit.create(FileApi::class.java).also { _fileApi = it }

    private var _folderApi: FolderApi? = null
    val folderApi: FolderApi get() = _folderApi ?: retrofit.create(FolderApi::class.java).also { _folderApi = it }

    private var _trashApi: TrashApi? = null
    val trashApi: TrashApi get() = _trashApi ?: retrofit.create(TrashApi::class.java).also { _trashApi = it }

    private var _uploadApi: UploadApi? = null
    val uploadApi: UploadApi get() = _uploadApi ?: retrofit.create(UploadApi::class.java).also { _uploadApi = it }

    private var _musicApi: MusicApi? = null
    val musicApi: MusicApi get() = _musicApi ?: retrofit.create(MusicApi::class.java).also { _musicApi = it }

    val authRepository: AuthRepository by lazy { AuthRepository(authApi, tokenStore) }
    val fileRepository: FileRepository by lazy { FileRepository(fileApi, tokenStore, lanResolver) }
    val folderRepository: FolderRepository by lazy { FolderRepository(folderApi) }
    val trashRepository: TrashRepository by lazy { TrashRepository(trashApi) }
    val uploadRepository: UploadRepository by lazy { UploadRepository(uploadApi, app.contentResolver) }
    val saveToDeviceRepository: SaveToDeviceRepository by lazy { SaveToDeviceRepository(authenticatedClient, fileRepository) }

    val musicDatabase: MusicDatabase by lazy { MusicDatabase.getInstance(app) }
    val musicPrefs: MusicPrefs by lazy { MusicPrefs(app) }
    val trackRepository: TrackRepository by lazy {
        TrackRepository(musicApi, musicDatabase, musicPrefs, fileRepository, lanResolver)
    }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(musicDatabase) }
    val downloadRepository: DownloadRepository by lazy { DownloadRepository(app, musicDatabase, musicPrefs) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(app) }
    val playerController: PlayerController by lazy {
        PlayerController(app, trackRepository, downloadRepository, networkMonitor)
    }

    val imageLoader by lazy {
        ImageLoader.Builder(app)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { authenticatedClient }
                    )
                )
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
