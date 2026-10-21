package com.luckyzyx.luckytool.ui.compose.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luckyzyx.luckytool.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune

/** 一级功能页只突出搜索，维护与查询操作收进更多菜单。 */
@Composable
internal fun FunctionTopBarActions(
    onSearch: () -> Unit,
    onRestart: () -> Unit,
    onVersionInfo: () -> Unit,
) {
    TopBarActionButton(
        onClick = onSearch,
        contentDescription = stringResource(R.string.menu_search),
        materialIcon = Icons.Outlined.Search,
        miuixIcon = MiuixIcons.Search,
    )
    TopBarOverflowMenu(
        items = listOf(
            TopBarOverflowItem(stringResource(R.string.menu_reboot), onRestart),
            TopBarOverflowItem(stringResource(R.string.menu_versioninfo), onVersionInfo),
        ),
        contentDescription = stringResource(R.string.menu_more),
    )
}

@Composable
internal fun HomeTopBarActions(onRestart: () -> Unit, onAbout: () -> Unit) {
    TopBarActionButton(
        onClick = onRestart,
        contentDescription = stringResource(R.string.menu_reboot),
        materialIcon = Icons.Filled.PowerSettingsNew,
        miuixIcon = MiuixIcons.Close2,
    )
    TopBarActionButton(
        onClick = onAbout,
        contentDescription = stringResource(R.string.about_title),
        materialIcon = Icons.Outlined.Info,
        miuixIcon = MiuixIcons.Info,
    )
}

@Composable
internal fun OtherTopBarActions(onOptimize: () -> Unit) {
    TopBarActionButton(
        onClick = onOptimize,
        contentDescription = stringResource(R.string.action_optimize_apps),
        materialIcon = Icons.Outlined.Tune,
        miuixIcon = MiuixIcons.Tune,
    )
}
