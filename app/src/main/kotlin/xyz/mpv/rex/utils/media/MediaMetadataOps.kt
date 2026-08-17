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

/**
 * Operations for mapping media files to high-level domain models like [VideoFolder].
 */
object MediaMetadataOps {
    private const val TAG = "MediaMetadataOps"

    /**
     * Scans and maps all folders containing media into [VideoFolder] domain objects.
     */
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
                        // A watched video must never remain in the folder's NEW badge.
                        // The index can still report it as new when its old playback
                        // state has timeRemaining == -1, so remove watched states that
                        // belong to this folder from the displayed new count.
                        val watchedInFolder = playbackStates.count { state ->
                            state.hasBeenWatched &&
                                java.io.File(state.mediaTitle).parent == folder.path
                        }
                        VideoFolder(
                            bucketId = folder.id,
                            name = folder.name,
                            path = folder.path,
                            videoCount = folder.videoCount,
                            audioCount = folder.audioCount,
                            totalSize = folder.totalSize,
                            totalDuration = folder.totalDuration,
                            lastModified = folder.lastModified,
                            newCount = (folder.newCount - watchedInFolder).coerceAtLeast(0),
                            unwatchedVideoCount = folder.unwatchedVideoCount
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping media folders", e)
                emptyList()
            }
        }
}
