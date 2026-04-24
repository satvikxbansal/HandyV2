package com.handy.core.notification

/**
 * Pure-Kotlin representation of one notification visible to
 * `HandyNotificationListenerService`. Scope §8.
 *
 * **Sensitive-value rule**: if [isRedacted] is true, [title] and [text]
 * are already the system's public / redacted variants. The listener
 * MUST NOT attempt to read unredacted contents on lock screen.
 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val subText: String?,
    val whenEpochMs: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val groupKey: String?,
    val isRedacted: Boolean,
    val canReply: Boolean,
    val canDismiss: Boolean,
)

/**
 * Grouping utility — scope §8.1. Pure data.
 *
 * Groups notifications by (packageName, groupKey) so "Messages from
 * Aarti (3)" surfaces as one row.
 */
fun List<NotificationSnapshot>.grouped(): List<NotificationGroup> {
    val map = LinkedHashMap<Pair<String, String?>, MutableList<NotificationSnapshot>>()
    for (n in this) {
        map.getOrPut(n.packageName to n.groupKey) { mutableListOf() }.add(n)
    }
    return map.map { (pair, items) ->
        NotificationGroup(
            packageName = pair.first,
            appLabel = items.firstNotNullOfOrNull { it.appLabel },
            groupKey = pair.second,
            items = items.toList(),
        )
    }
}

data class NotificationGroup(
    val packageName: String,
    val appLabel: String?,
    val groupKey: String?,
    val items: List<NotificationSnapshot>,
) {
    val count: Int get() = items.size
    val latestText: String? get() = items.maxByOrNull { it.whenEpochMs }?.text
}
