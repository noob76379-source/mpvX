package xyz.mpv.rex.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.LocalThemeTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * An action that appears in the selection-mode overflow (⋮) menu.
 * Pass a list of these via [BrowserTopBar.selectionOverflowActions] to populate the menu.
 */
data class SelectionOverflowAction(
  val icon: ImageVector,
  val label: String,
  val onClick: () -> Unit,
)

/**
 * Unified top bar for browser screens that switches between normal and selection modes
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserTopBar(
  title: String,
  isInSelectionMode: Boolean,
  selectedCount: Int,
  totalCount: Int,
  onCancelSelection: () -> Unit,
  modifier: Modifier = Modifier,
  onBackClick: (() -> Unit)? = null,
  onSortClick: (() -> Unit)? = null,
  onSearchClick: (() -> Unit)? = null,
  onSettingsClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onRenameClick: (() -> Unit)? = null,
  isSingleSelection: Boolean = false,
  onInfoClick: (() -> Unit)? = null,
  onPlayClick: (() -> Unit)? = null,
  onSelectAll: (() -> Unit)? = null,
  onInvertSelection: (() -> Unit)? = null,
  onDeselectAll: (() -> Unit)? = null,
  selectionOverflowActions: List<SelectionOverflowAction> = emptyList(),
  normalOverflowActions: List<SelectionOverflowAction> = emptyList(),
  additionalActions: @Composable RowScope.() -> Unit = { },
  onTitleLongPress: (() -> Unit)? = null,
  useRemoveIcon: Boolean = false,
  deleteInOverflow: Boolean = false,
  isHomeScreen: Boolean = false,
) {
  if (isInSelectionMode) {
    SelectionTopBar(
      selectedCount = selectedCount,
      totalCount = totalCount,
      onCancel = onCancelSelection,
      onDelete = onDeleteClick,
      onRename = onRenameClick,
      isSingleSelection = isSingleSelection,
      onInfo = onInfoClick,
      onPlay = onPlayClick,
      onSelectAll = onSelectAll,
      onInvertSelection = onInvertSelection,
      onDeselectAll = onDeselectAll,
      overflowActions = selectionOverflowActions,
      modifier = modifier,
      useRemoveIcon = useRemoveIcon,
      deleteInOverflow = deleteInOverflow,
    )
  } else {
    NormalTopBar(
      title = title,
      onBackClick = onBackClick,
      onSortClick = onSortClick,
      onSearchClick = onSearchClick,
      onSettingsClick = onSettingsClick,
      normalOverflowActions = normalOverflowActions,
      additionalActions = additionalActions,
      modifier = modifier,
      onTitleLongPress = onTitleLongPress,
      isHomeScreen = isHomeScreen,
    )
  }
}

/**
 * Normal mode top bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalTopBar(
  title: String,
  onBackClick: (() -> Unit)?,
  onSortClick: (() -> Unit)?,
  onSearchClick: (() -> Unit)?,
  onSettingsClick: (() -> Unit)?,
  normalOverflowActions: List<SelectionOverflowAction>,
  additionalActions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  onTitleLongPress: (() -> Unit)?,
  isHomeScreen: Boolean = false,
) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val themeTransition = LocalThemeTransitionState.current
  val coroutineScope = rememberCoroutineScope()
  
  // Track title bounds for animation position
  val titleBounds = remember { mutableStateOf(Rect.Zero) }
  
  // Helper function to toggle dark mode
  fun toggleDarkMode() {
    when (darkMode) {
      DarkMode.System -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Light -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.System)
      } else {
        preferences.darkMode.set(DarkMode.Dark)
      }
      DarkMode.Dark -> if (darkTheme) {
        preferences.darkMode.set(DarkMode.Light)
      } else {
        preferences.darkMode.set(DarkMode.System)
      }
    }
  }

  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color.Black
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    title = {
      val titleModifier = Modifier
        .onGloballyPositioned { coordinates ->
          titleBounds.value = coordinates.boundsInWindow()
        }
        .pointerInput(onTitleLongPress) {
          detectTapGestures(
            onTap = { localOffset ->
              // Don't allow theme change if animation is in progress
              if (themeTransition?.isAnimating == true) return@detectTapGestures
              
              // Calculate window position for circular reveal
              val windowOffset = Offset(
                titleBounds.value.left + localOffset.x,
                titleBounds.value.top + localOffset.y
              )
              themeTransition?.startTransition(windowOffset)
              // Delay theme change to allow overlay to display first
              coroutineScope.launch {
                delay(50)
                toggleDarkMode()
              }
            },
            onLongPress = if (onTitleLongPress != null) {
              { onTitleLongPress() }
            } else null
          )
        }

      val isAppTitleHeader = isHomeScreen || title == stringResource(R.string.app_name) || title == "mpvX"
      var showNewName by remember { mutableStateOf(false) }

      if (isAppTitleHeader) {
        LaunchedEffect(Unit) {
          while (true) {
            delay(4000)
            showNewName = !showNewName
          }
        }
      }

      val displayTitle = if (isAppTitleHeader && showNewName) "REX Player" else title

      AnimatedContent(
        targetState = displayTitle,
        transitionSpec = {
          (fadeIn(animationSpec = tween(600)) + slideInVertically { height -> height / 2 })
            .togetherWith(fadeOut(animationSpec = tween(600)) + slideOutVertically { height -> -height / 2 })
        },
        label = "HeaderTitleTransition",
      ) { currentTitle ->
        Text(
          currentTitle,
          style =
            if (onBackClick == null) {
              MaterialTheme.typography.headlineMediumEmphasized
            } else {
              MaterialTheme.typography.headlineSmall
            },
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier =
            titleModifier.then(
              if (onBackClick == null) {
                Modifier.padding(start = 8.dp)
              } else {
                Modifier
              },
            ),
        )
      }
    },
    navigationIcon = {
      if (onBackClick != null) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.padding(horizontal = 2.dp),
