package xyz.mpv.rex.utils.media

import android.content.Context
import android.util.Log
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Operations for mapping media files to high-level domain models like [VideoFolder].
 */
object MediaMetadataOps {
    private const val TAG = "MediaMetadataOps"

    suspend fun getAllMediaFolders(context: Context): List<VideoFolder> =
        withContext(Dispatchers.IO) {
            try {
                val koin = GlobalContext.get()
                val browserPreferences = koin.get<BrowserPreferences>()
                val appearancePreferences = koin.get<AppearancePreferences>()
                val playbackStateRepository = koin.get<PlaybackStateRepository>()

                val isAudioEnabled = browserPreferences.showAudioFiles.get()
                val playbackStates = playbackStateRepository.getAllPlaybackStates()
                val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
                val recentThresholdMillis = thresholdDays * 24L * 60L * 60L * 1000L
                val now = System.currentTimeMillis()

                val foldersPreferences = koin.get<xyz.mpv.rex.preferences.FoldersPreferences>()
                val blacklistedFolders = foldersPreferences.blacklistedFolders.get()

                val hybridIndex = koin.get<HybridMediaIndexRepository>()
                hybridIndex.ensureFreshIfEmpty()
                val folders = hybridIndex.getFlatFolders(
                    playbackStates = playbackStates,
                    thresholdDays = thresholdDays,
                    watchedThreshold = browserPreferences.watchedThreshold.get(),
                )

                folders
                    .filter { folder ->
                        (isAudioEnabled || folder.videoCount > 0) && folder.path !in blacklistedFolders
                    }
                    .map { folder ->
                        // The index provides the base recently-added count. Playback state in mpvX
                        // can use a path representation different from the index identity, so match
                        // opened videos by normalized file path and only subtract videos that are
                        // still inside the same recent window. This keeps the initial count intact
                        // while making it decrease after a recently added video is opened.
                        val folderPath = folder.path.trimEnd(File.separatorChar)
                        val openedRecentVideos = playbackStates.asSequence()
                            .mapNotNull { state -> state.mediaTitle.takeIf { it.isNotBlank() } }
                            .map { File(it) }
                            .filter { file ->
                                val parent = file.parent?.trimEnd(File.separatorChar)
                                parent == folderPath && file.exists() &&
                                    now - file.lastModified() <= recentThresholdMillis
                            }
                            .map { it.absolutePath }
                            .distinct()
                            .count()

                        VideoFolder(
                            bucketId = folder.id,
                            name = folder.name,
                            path = folder.path,
                            videoCount = folder.videoCount,
                            audioCount = folder.audioCount,
                            totalSize = folder.totalSize,
                            totalDuration = folder.totalDuration,
                            lastModified = folder.lastModified,
                            newCount = (folder.newCount - openedRecentVideos).coerceAtLeast(0),
                            unwatchedVideoCount = folder.unwatchedVideoCount
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping media folders", e)
                emptyList()
            }
        }
}
