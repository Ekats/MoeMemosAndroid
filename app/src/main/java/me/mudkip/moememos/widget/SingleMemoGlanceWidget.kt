package me.mudkip.moememos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalGlanceId
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mudkip.moememos.MainActivity
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.UserSettings
import me.mudkip.moememos.data.service.MemoService
import me.mudkip.moememos.ext.currentUserSettings
import me.mudkip.moememos.util.MemoColor
import timber.log.Timber
import java.time.Instant

/**
 * Shows one memo chosen when the widget is placed (SingleMemoWidgetConfigurationActivity), as much
 * as fits. Tapping anywhere on it opens that memo in the editor.
 */
class SingleMemoGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val memoService = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        ).memoService()

        provideContent(createContent(context, memoService))
    }

    // Keep composable lambda captures outside the coroutine state machine so Compose
    // can collect stack trace mappings for the widget content.
    private fun createContent(context: Context, memoService: MemoService): @Composable () -> Unit = {
        val prefs = currentState<Preferences>()
        val userSettings by context.currentUserSettings.collectAsState(initial = UserSettings())
        GlanceTheme {
            WidgetContent(context, memoService, prefs, userSettings)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        memoService: MemoService,
        prefs: Preferences,
        userSettings: UserSettings
    ) {
        val memoId = prefs[SingleMemoWidgetKeys.memoId]
        val refreshKey = prefs[SingleMemoWidgetKeys.refreshKey] ?: 0L
        var memo by remember { mutableStateOf<MemoEntity?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(memoId, refreshKey) {
            withContext(Dispatchers.IO) {
                try {
                    memo = memoId?.let { memoService.getRepository().getMemo(it) }?.takeUnless { it.isDeleted }
                } catch (e: Exception) {
                    Timber.tag("SingleMemoWidget").e(e, "Failed to load memo")
                    memo = null
                } finally {
                    isLoading = false
                }
            }
        }

        val loadedMemo = memo
        val color = loadedMemo?.let { MemoColor.fromKey(userSettings.memoColors[it.identifier]) }
        // No memo (none chosen yet, or it was deleted): a tap picks another one instead
        val tapAction = if (loadedMemo != null) {
            actionStartActivity(editMemoIntent(context, loadedMemo.identifier))
        } else {
            chooseMemoAction(context)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ImageProvider(
                        when {
                            color != null && loadedMemo?.pinned == true -> color.widgetCardPinned
                            color != null -> color.widgetCard
                            loadedMemo?.pinned == true -> R.drawable.widget_card_pinned_background
                            else -> R.drawable.widget_card_background
                        }
                    )
                )
                .clickable(tapAction)
                .padding(12.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            when {
                isLoading -> Unit
                loadedMemo == null -> {
                    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = context.getString(
                                if (memoId == null) R.string.single_memo_widget_choose
                                else R.string.single_memo_widget_missing
                            ),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
                else -> {
                    // No scrolling list: a list keeps taps on its empty space for scrolling, and the
                    // whole widget must open the memo. Text that does not fit is cut off.
                    Box(modifier = GlanceModifier.fillMaxSize()) {
                        AndroidRemoteViews(
                            remoteViews = widgetMemoTextViews(context, loadedMemo.content, maxLines = 0),
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                    }
                    DatePill(context, loadedMemo)
                }
            }
        }
    }

    @Composable
    private fun DatePill(context: Context, memo: MemoEntity) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.widget_date_bubble))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    memo.date.toEpochMilli(),
                    Instant.now().toEpochMilli(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
            if (memo.visibility != MemoVisibility.PUBLIC) {
                Spacer(modifier = GlanceModifier.width(3.dp))
                Image(
                    provider = ImageProvider(android.R.drawable.ic_lock_lock),
                    contentDescription = context.getString(R.string.widget_private),
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
            if (memo.pinned) {
                Spacer(modifier = GlanceModifier.width(3.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_pin),
                    contentDescription = context.getString(R.string.widget_pinned),
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

    @Composable
    private fun chooseMemoAction(context: Context): Action {
        val glanceId = LocalGlanceId.current
        var appWidgetId by remember { mutableStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
        LaunchedEffect(glanceId) {
            appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        }
        return actionStartActivity(
            Intent(context, SingleMemoWidgetConfigurationActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }
}

// Always the editor, whatever the app's edit gesture setting is
private fun editMemoIntent(context: Context, memoId: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_EDIT_MEMO
        putExtra(MainActivity.EXTRA_MEMO_ID, memoId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

object SingleMemoWidgetKeys {
    val memoId = stringPreferencesKey("single_memo_id")
    val refreshKey = longPreferencesKey("refresh_key")
}
