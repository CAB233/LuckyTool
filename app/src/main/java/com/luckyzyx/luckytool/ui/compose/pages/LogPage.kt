package com.luckyzyx.luckytool.ui.compose.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.compose.components.material.MaterialPageScaffold

/**
 * Logs 页：旧 LoggerFragment 的 fragment_logs.xml 仅一个居中占位文本
 * （"重新规划中......"）。日志功能开放前不展示没有实现的操作按钮。
 */
@Composable
fun LogPage() {
    MaterialPageScaffold(
        title = stringResource(R.string.nav_log),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "重新规划中......", fontSize = 18.sp)
        }
    }
}
