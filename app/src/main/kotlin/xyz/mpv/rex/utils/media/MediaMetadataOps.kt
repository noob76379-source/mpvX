package xyz.mpv.rex.utils.media

import android.content.Context
import android.util.Log
import xyz.mpv.rex.database.repository.HybridMediaIndexRepository
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.BrowserPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

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
                val openedMedia = playbackStates
                    .map { it.mediaTitle.trim() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()
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
                        // Calculate the badge from the actual videos in this folder so playback
                        // state is matched using the same identifiers the player can persist.
                        val newCount = hybridIndex.getVideosInFolder(folder.id)
                            .asSequence()
                            .filter { video -> !video.isAudio }
                            .filter { video -> now - video.dateModified in 0..recentThresholdMillis }
                            .count { video ->
                                val file = File(video.path)
                                val identifiers = setOf(
                                    video.title,
                                    video.path,
                                    video.uri.toString(),
                                    file.absolutePath,
                                    file.name,
                                ).map { it.trim() }
                                identifiers.none { it in openedMedia }
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
                            newCount = newCount,
                            unwatchedVideoCount = folder.unwatchedVideoCount,
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping media folders", e)
                emptyList()
            }
        }
}
