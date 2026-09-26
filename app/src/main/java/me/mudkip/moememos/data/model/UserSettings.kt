package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemoEditGesture {
    NONE,
    SINGLE,
    DOUBLE,
    LONG,
}

@Serializable
data class UserSettings(
    val draft: String = "",
    val acceptedUnsupportedSyncVersions: List<String> = emptyList(),
    val editGesture: MemoEditGesture = MemoEditGesture.NONE,
    val autosave: Boolean = false,
    // Memo identifier -> MemoColor key. Strings, not the enum, so an unknown key can't fail decoding.
    val memoColors: Map<String, String> = emptyMap(),
    // Lines of each memo shown in the memos widget; 0 shows the whole memo
    val widgetLinesPerMemo: Int = 3,
)
