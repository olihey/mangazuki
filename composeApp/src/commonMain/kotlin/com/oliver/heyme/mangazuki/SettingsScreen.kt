package com.oliver.heyme.mangazuki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oliver.heyme.mangazuki.core.domain.ReadingMode
import com.oliver.heyme.mangazuki.core.domain.formatDateTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import manga_reader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun TitleLanguage.label(): String = when (this) {
    TitleLanguage.FILE -> stringResource(Res.string.title_language_file)
    TitleLanguage.ANILIST_ROMAJI -> stringResource(Res.string.title_language_romaji)
    TitleLanguage.ANILIST_ENGLISH -> stringResource(Res.string.title_language_english)
    TitleLanguage.ANILIST_NATIVE -> stringResource(Res.string.title_language_native)
}

@Composable
fun StartScreen.label(): String = when (this) {
    StartScreen.LIBRARY -> stringResource(Res.string.start_screen_library)
    StartScreen.YOUR_PAGE -> stringResource(Res.string.start_screen_your_page)
}

@Composable
fun MetadataProviderChoice.label(): String = when (this) {
    MetadataProviderChoice.ANILIST -> stringResource(Res.string.metadata_provider_anilist)
    MetadataProviderChoice.KITSU -> stringResource(Res.string.metadata_provider_kitsu)
}

/** Shared with the reader's chrome quick-switcher, so both use identical wording. */
@Composable
fun ReadingMode.label(): String = when (this) {
    ReadingMode.PAGED_LTR -> stringResource(Res.string.reading_mode_paged_ltr)
    ReadingMode.PAGED_RTL -> stringResource(Res.string.reading_mode_paged_rtl)
    ReadingMode.VERTICAL_PAGED -> stringResource(Res.string.reading_mode_vertical_paged)
    ReadingMode.VERTICAL_CONTINUOUS -> stringResource(Res.string.reading_mode_vertical_continuous)
}

/** Compact form for the chrome's quick-switcher button, where space is tight. */
@Composable
fun ReadingMode.shortLabel(): String = when (this) {
    ReadingMode.PAGED_LTR -> stringResource(Res.string.reading_mode_short_paged_ltr)
    ReadingMode.PAGED_RTL -> stringResource(Res.string.reading_mode_short_paged_rtl)
    ReadingMode.VERTICAL_PAGED -> stringResource(Res.string.reading_mode_short_vertical_paged)
    ReadingMode.VERTICAL_CONTINUOUS -> stringResource(Res.string.reading_mode_short_vertical_continuous)
}

/** Reader settings (PLAN.md §8.1): default reading mode, tap-zone layout, volume-key paging. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: ReaderPreferences,
    appPreferences: AppPreferences,
    onBack: () -> Unit,
    onResetLibrary: () -> Unit,
    syncState: StateFlow<SyncState> = kotlinx.coroutines.flow.MutableStateFlow(SyncState.SignedOut),
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onBackgroundSyncEnabledChanged: (Boolean) -> Unit = {},
    fetchProgressJson: suspend () -> String? = { null },
    fetchMetadataAliasesJson: suspend () -> String? = { null },
    clearProgressJson: suspend () -> Unit = {},
    clearMetadataAliasesJson: suspend () -> Unit = {},
    isDebugBuild: Boolean = false,
) {
    var readingMode by remember { mutableStateOf(prefs.defaultReadingMode) }
    var invertTapZones by remember { mutableStateOf(prefs.invertTapZones) }
    var volumeKeyPaging by remember { mutableStateOf(prefs.volumeKeyPaging) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var viewJsonDialog by remember { mutableStateOf<ViewJsonDialogState?>(null) }
    var clearTarget by remember { mutableStateOf<DebugFile?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val titleLanguage by appPreferences.titleLanguage.collectAsState()
    val startScreen by appPreferences.startScreen.collectAsState()
    val metadataProvider by appPreferences.metadataProvider.collectAsState()
    val syncEnabled by appPreferences.syncEnabled.collectAsState()
    val lastSyncedAt by appPreferences.lastSyncedAt.collectAsState()
    val metadataAliasSyncEnabled by appPreferences.metadataAliasSyncEnabled.collectAsState()
    val lastMetadataAliasSyncedAt by appPreferences.lastMetadataAliasSyncedAt.collectAsState()
    val backgroundSyncEnabled by appPreferences.backgroundSyncEnabled.collectAsState()
    val sync by syncState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { BackIcon() }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(Res.string.settings_section_series_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_section_series_title_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TitleLanguage.entries.forEach { language ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { appPreferences.setTitleLanguage(language) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = language == titleLanguage, onClick = { appPreferences.setTitleLanguage(language) })
                    Text(language.label())
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                stringResource(Res.string.settings_section_start_screen),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_section_start_screen_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            StartScreen.entries.forEach { screen ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { appPreferences.setStartScreen(screen) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = screen == startScreen, onClick = { appPreferences.setStartScreen(screen) })
                    Text(screen.label())
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                stringResource(Res.string.settings_section_metadata_provider),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_section_metadata_provider_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            MetadataProviderChoice.entries.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { appPreferences.setMetadataProvider(choice) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = choice == metadataProvider, onClick = { appPreferences.setMetadataProvider(choice) })
                    Text(choice.label())
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                stringResource(Res.string.settings_section_reading_mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_section_reading_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ReadingMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            readingMode = mode
                            prefs.defaultReadingMode = mode
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = mode == readingMode, onClick = {
                        readingMode = mode
                        prefs.defaultReadingMode = mode
                    })
                    Text(mode.label())
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SettingSwitchRow(
                title = stringResource(Res.string.settings_swap_tap_zones_title),
                subtitle = stringResource(Res.string.settings_swap_tap_zones_subtitle),
                checked = invertTapZones,
                onCheckedChange = {
                    invertTapZones = it
                    prefs.invertTapZones = it
                },
            )
            SettingSwitchRow(
                title = stringResource(Res.string.settings_volume_keys_title),
                subtitle = stringResource(Res.string.settings_volume_keys_subtitle),
                checked = volumeKeyPaging,
                onCheckedChange = {
                    volumeKeyPaging = it
                    prefs.volumeKeyPaging = it
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                stringResource(Res.string.settings_section_cloud_sync),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_section_cloud_sync_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when (val state = sync) {
                is SyncState.SignedOut -> {
                    TextButton(onClick = onSignIn, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(Res.string.settings_sign_in_google))
                    }
                }
                is SyncState.SigningIn -> {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text(stringResource(Res.string.settings_signing_in), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is SyncState.SignedIn -> {
                    val notSyncedYet = stringResource(Res.string.settings_sync_not_synced_yet)
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_sync_progress_title),
                        subtitle = stringResource(Res.string.settings_sync_progress_subtitle),
                        checked = syncEnabled,
                        onCheckedChange = appPreferences::setSyncEnabled,
                        byline = lastSyncedAt?.let { stringResource(Res.string.settings_sync_last_synced, formatDateTime(it)) } ?: notSyncedYet,
                    )
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_sync_metadata_title),
                        subtitle = stringResource(Res.string.settings_sync_metadata_subtitle),
                        checked = metadataAliasSyncEnabled,
                        onCheckedChange = appPreferences::setMetadataAliasSyncEnabled,
                        byline = lastMetadataAliasSyncedAt?.let { stringResource(Res.string.settings_sync_last_synced, formatDateTime(it)) } ?: notSyncedYet,
                    )
                    SettingSwitchRow(
                        title = stringResource(Res.string.settings_sync_background_title),
                        subtitle = stringResource(Res.string.settings_sync_background_subtitle),
                        checked = backgroundSyncEnabled,
                        onCheckedChange = onBackgroundSyncEnabledChanged,
                    )
                    TextButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(Res.string.settings_sign_out)) }
                }
                is SyncState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextButton(onClick = onSignIn, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(Res.string.settings_sign_in_google))
                    }
                }
            }

            if (isDebugBuild && sync is SyncState.SignedIn) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Text(
                    stringResource(Res.string.settings_section_debug),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                )
                Text(
                    stringResource(Res.string.settings_section_debug_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = {
                        viewJsonDialog = ViewJsonDialogState.Loading(DebugFile.PROGRESS)
                        coroutineScope.launch {
                            viewJsonDialog = ViewJsonDialogState.Loaded(DebugFile.PROGRESS, fetchProgressJson())
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(Res.string.settings_debug_view_progress)) }
                TextButton(
                    onClick = {
                        viewJsonDialog = ViewJsonDialogState.Loading(DebugFile.METADATA_ALIASES)
                        coroutineScope.launch {
                            viewJsonDialog = ViewJsonDialogState.Loaded(DebugFile.METADATA_ALIASES, fetchMetadataAliasesJson())
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(Res.string.settings_debug_view_metadata_aliases)) }
                TextButton(
                    onClick = { clearTarget = DebugFile.PROGRESS },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(Res.string.settings_debug_clear_progress)) }
                TextButton(
                    onClick = { clearTarget = DebugFile.METADATA_ALIASES },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(Res.string.settings_debug_clear_metadata_aliases)) }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text(
                stringResource(Res.string.settings_reset_library_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
            )
            Text(
                stringResource(Res.string.settings_reset_library_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TextButton(
                onClick = { showResetConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(Res.string.settings_reset_library_title)) }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(Res.string.settings_reset_confirm_title)) },
            text = {
                Text(stringResource(Res.string.settings_reset_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetLibrary()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.settings_reset_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    viewJsonDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { viewJsonDialog = null },
            title = { Text(state.file.fileName) },
            text = {
                when (state) {
                    is ViewJsonDialogState.Loading -> {
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text(stringResource(Res.string.settings_debug_fetching))
                        }
                    }
                    is ViewJsonDialogState.Loaded -> {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                state.json ?: stringResource(Res.string.settings_debug_not_signed_in),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewJsonDialog = null }) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }

    clearTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text(stringResource(Res.string.settings_debug_clear_confirm_title, target.fileName)) },
            text = {
                Text(stringResource(Res.string.settings_debug_clear_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearTarget = null
                        coroutineScope.launch {
                            when (target) {
                                DebugFile.PROGRESS -> clearProgressJson()
                                DebugFile.METADATA_ALIASES -> clearMetadataAliasesJson()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.settings_debug_clear_action)) }
            },
            dismissButton = {
                TextButton(onClick = { clearTarget = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** Settings' Debug section (PLAN.md §10) -- which of the two `appDataFolder` files a view/clear
 * action targets. */
private enum class DebugFile(val fileName: String) {
    PROGRESS("progress.json"),
    METADATA_ALIASES("metadata_aliases.json"),
}

/** A fetch takes a network round trip, so this needs a loading state distinct from "loaded but
 * null" (not signed in, or the file doesn't exist on Drive yet -- both normal). */
private sealed interface ViewJsonDialogState {
    val file: DebugFile
    data class Loading(override val file: DebugFile) : ViewJsonDialogState
    data class Loaded(override val file: DebugFile, val json: String?) : ViewJsonDialogState
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    byline: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (byline != null) {
                Text(byline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
