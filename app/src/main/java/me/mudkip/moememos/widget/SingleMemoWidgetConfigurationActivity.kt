package me.mudkip.moememos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.service.MemoService
import me.mudkip.moememos.ui.security.AppLockGate
import me.mudkip.moememos.ui.theme.MoeMemosTheme
import me.mudkip.moememos.util.AppLanguage
import javax.inject.Inject

/** Picks the memo a single-memo widget shows; opened when the widget is placed or reconfigured. */
@AndroidEntryPoint
class SingleMemoWidgetConfigurationActivity : FragmentActivity() {

    @Inject
    lateinit var memoService: MemoService

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            MoeMemosTheme {
                AppLockGate {
                    PickerScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerScreen() {
        var memos by remember { mutableStateOf<List<MemoEntity>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var query by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            try {
                memoService.getRepository().listMemos().suspendOnSuccess {
                    memos = data.sortedWith(
                        compareByDescending<MemoEntity> { it.pinned }.thenByDescending { it.date }
                    )
                }
            } finally {
                isLoading = false
            }
        }

        val shown = if (query.isBlank()) memos else memos.filter { it.content.contains(query.trim(), ignoreCase = true) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.single_memo_widget_choose)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_memos))
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.identifier }) { memo ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        memoPreview(memo),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(
                                            memo.date.toEpochMilli(),
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        ).toString(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                trailingContent = if (memo.pinned) {
                                    { Icon(Icons.Outlined.PushPin, contentDescription = null) }
                                } else {
                                    null
                                },
                                modifier = Modifier.clickable { choose(memo) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Plain text of the memo, without markdown markers, for the list
    private fun memoPreview(memo: MemoEntity): String =
        WidgetMarkdown.render(memo.content).text.lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" · ")
            .ifBlank { memo.content }

    private fun choose(memo: MemoEntity) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@SingleMemoWidgetConfigurationActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@SingleMemoWidgetConfigurationActivity, glanceId) { prefs ->
                prefs[SingleMemoWidgetKeys.memoId] = memo.identifier
                prefs[SingleMemoWidgetKeys.refreshKey] = (prefs[SingleMemoWidgetKeys.refreshKey] ?: 0L) + 1
            }
            SingleMemoGlanceWidget().update(this@SingleMemoWidgetConfigurationActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
