package me.mudkip.moememos.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.navigateToMemoEditor
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.util.MemoColor
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState

@Composable
fun MemosCard(
    memo: MemoEntity,
    onClick: (MemoEntity) -> Unit,
    editGesture: MemoEditGesture = MemoEditGesture.NONE,
    previewMode: Boolean = false,
    showSyncStatus: Boolean = false,
    onTagClick: ((String) -> Unit)? = null
) {
    val memosViewModel = LocalMemos.current
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()

    val cardModifier = Modifier
        .padding(horizontal = 15.dp, vertical = 10.dp)
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (editGesture == MemoEditGesture.SINGLE) {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                } else {
                    onClick(memo)
                }
            },
            onLongClick = if (editGesture == MemoEditGesture.LONG) {
                {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                }
            } else {
                null
            },
            onDoubleClick = if (editGesture == MemoEditGesture.DOUBLE) {
                {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                }
            } else {
                null
            }
        )

    val memoColors by memosViewModel.memoColors.collectAsStateWithLifecycle()
    val memoColor = memoColors[memo.identifier]

    Card(
        modifier = cardModifier,
        border = if (memo.pinned) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = memoColor?.let { CardDefaults.cardColors(containerColor = colorResource(it.color)) }
            ?: CardDefaults.cardColors()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(start = 15.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        memo.date.toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                if (showSyncStatus && memo.needsSync) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = R.string.memo_sync_pending.string,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (LocalUserState.current.currentUser?.defaultVisibility != memo.visibility) {
                    Icon(
                        memo.visibility.icon,
                        contentDescription = stringResource(memo.visibility.titleResource),
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                MemosCardActionButton(memo)
            }

            MemoContent(
                memo,
                previewMode = previewMode,
                checkboxChange = { checked, startOffset, endOffset ->
                    scope.launch {
                        var text = memo.content.substring(startOffset, endOffset)
                        text = if (checked) {
                            text.replace("[ ]", "[x]")
                        } else {
                            text.replace("[x]", "[ ]")
                        }
                        memosViewModel.editMemo(
                            memo.identifier,
                            memo.content.replaceRange(startOffset, endOffset, text),
                            memo.resources,
                            memo.visibility
                        )
                    }
                },
                onViewMore = {
                    onClick(memo)
                },
                onTagClick = onTagClick
            )
        }
    }
}

@Composable
fun MemosCardActionButton(
    memo: MemoEntity,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    val memoLabel = stringResource(R.string.memo)

    Box {
        ActionIconButton(label = stringResource(R.string.more_options), onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (memo.pinned) {
                DropdownMenuItem(
                    text = { Text(R.string.unpin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, false).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PinDrop,
                            contentDescription = null
                        )
                    })
            } else {
                DropdownMenuItem(
                    text = { Text(R.string.pin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, true).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                text = { Text(R.string.edit.string) },
                onClick = {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.memo_color.string) },
                onClick = {
                    menuExpanded = false
                    showColorDialog = true
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Palette,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.share.string) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, memo.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.copy.string) },
                onClick = {
                    clipboardManager?.setPrimaryClip(
                        ClipData.newPlainText(memoLabel, memo.content)
                    )
                    menuExpanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null
                    )
                })
            if (currentAccount !is Account.Local) {
                DropdownMenuItem(
                    text = { Text(R.string.copy_link.string) },
                    onClick = {
                        memosViewModel.host.value?.let { host ->
                            val memoUrl = "$host/${memo.remoteId ?: memo.identifier}"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, memoUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                text = { Text(R.string.archive.string) },
                onClick = {
                    scope.launch {
                        memosViewModel.archiveMemo(memo.identifier).suspendOnSuccess {
                            menuExpanded = false
                        }
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.delete.string) },
                onClick = {
                    showDeleteDialog = true
                    menuExpanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                })
        }
    }

    if (showColorDialog) {
        val memoColors by memosViewModel.memoColors.collectAsStateWithLifecycle()
        MemoColorDialog(
            current = memoColors[memo.identifier],
            onSelect = { color ->
                memosViewModel.setMemoColor(memo.identifier, color)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete_this_memo.string) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            memosViewModel.deleteMemo(memo.identifier).suspendOnSuccess {
                                showDeleteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(R.string.confirm.string)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }
}

@Composable
private fun MemoColorDialog(
    current: MemoColor?,
    onSelect: (MemoColor?) -> Unit,
    onDismiss: () -> Unit
) {
    val options: List<MemoColor?> = listOf(null) + MemoColor.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.memo_color.string) },
        text = {
            Column {
                options.chunked(5).forEach { row ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { color ->
                            val selected = color == current
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (color == null) MaterialTheme.colorScheme.surface
                                        else colorResource(color.color)
                                    )
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        onClickLabel = color?.key ?: R.string.memo_color_none.string
                                    ) { onSelect(color) }
                            ) {
                                if (color == null) {
                                    Icon(
                                        Icons.Outlined.FormatColorReset,
                                        contentDescription = R.string.memo_color_none.string,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (selected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(R.string.close.string)
            }
        }
    )
}
