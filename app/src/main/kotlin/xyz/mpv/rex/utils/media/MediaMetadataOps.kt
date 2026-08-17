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
                        // Folder NEW count must be based on the current watched state.
                        // PlaybackStateEntity.mediaTitle is not guaranteed to be a full path,
                        // so match the state against the folder using several stable forms.
                        val watchedInFolder = playbackStates.count { state ->
                            if (!state.hasBeenWatched) return@count false

                            val title = state.mediaTitle.trim()
                            if (title.isEmpty()) return@count false

                            val file = java.io.File(title)
                            val parent = file.parent?.trimEnd(java.io.File.separatorChar)
                            val folderPath = folder.path.trimEnd(java.io.File.separatorChar)
                            val fileName = file.name
                            val titleWithoutExtension = fileName.substringBeforeLast('.', fileName)

                            parent == folderPath ||
                                fileName.equals(title, ignoreCase = true) ||
                                java.io.File(folderPath, fileName).exists() ||
                                java.io.File(folderPath, title).exists() ||
                                folder.name.equals(title, ignoreCase = true) ||
                                folder.name.equals(titleWithoutExtension, ignoreCase = true)
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
