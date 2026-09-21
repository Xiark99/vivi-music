/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.ui.screens.settings

import com.music.vivi.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.LocalPlayerAwareWindowInsets
import com.music.vivi.ui.component.IconButton
import com.music.vivi.ui.component.ExpressiveSettingGroup
import com.music.vivi.ui.component.Material3SettingsItem
import com.music.vivi.ui.screens.Screens
import com.music.vivi.ui.utils.backToMain


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
        )

        // Group 1: Media & Player Experience
        ExpressiveSettingGroup(
            itemMinHeight = 64.dp,
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.appearance)) },
                        description = { Text(stringResource(R.string.setting_appearance_desc)) },
                        onClick = { navController.navigate("settings/appearance") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.earbud_case),
                        title = { Text(stringResource(R.string.player_and_audio)) },
                        description = { Text(stringResource(R.string.setting_player_desc)) },
                        onClick = { navController.navigate("settings/player") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.group),
                        title = { Text(stringResource(R.string.listen_together)) },
                        description = { Text(stringResource(R.string.setting_listen_together_desc)) },
                        onClick = { navController.navigate(Screens.ListenTogether.route) }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.content)) },
                        description = { Text(stringResource(R.string.setting_content_desc)) },
                        onClick = { navController.navigate("settings/content") }
                    )
                )
            }
        )

        // Group 2: Features & Data
        ExpressiveSettingGroup(
            itemMinHeight = 64.dp,
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.translate),
                        title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                        description = { Text(stringResource(R.string.setting_ai_lyrics_translation_desc)) },
                        onClick = { navController.navigate("settings/ai") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.security),
                        title = { Text(stringResource(R.string.privacy)) },
                        description = { Text(stringResource(R.string.setting_privacy_desc)) },
                        onClick = { navController.navigate("settings/privacy") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.history),
                        title = { Text(stringResource(R.string.listening_summary)) },
                        description = { Text(stringResource(R.string.setting_listening_summary_desc)) },
                        onClick = { navController.navigate("settings/listening_summary") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(stringResource(R.string.storage)) },
                        description = { Text(stringResource(R.string.setting_storage_desc)) },
                        onClick = { navController.navigate("settings/storage") }
                    )
                )
            }
        )

        // Group 3: Account & Backup
        ExpressiveSettingGroup(
            itemMinHeight = 64.dp,
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.google),
                        title = { Text(stringResource(R.string.account)) },
                        description = { Text(stringResource(R.string.setting_account_desc)) },
                        onClick = { navController.navigate("settings/account") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.restore),
                        title = { Text(stringResource(R.string.backup_restore)) },
                        description = { Text(stringResource(R.string.setting_backup_restore_desc)) },
                        onClick = { navController.navigate("settings/backup_restore") }
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(50.dp))
    }

    TopAppBar(
        title = {
//            Text(stringResource(R.string.settings))
                },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
