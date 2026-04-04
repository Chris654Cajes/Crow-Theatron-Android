package com.crowtheatron.app.ui

import com.crowtheatron.app.data.VideoEntity

sealed interface LibraryListItem {
    data class Header(val folder: String) : LibraryListItem
    data class VideoRow(val entity: VideoEntity) : LibraryListItem
}

fun buildGroupedItems(videos: List<VideoEntity>): List<LibraryListItem> {
    if (videos.isEmpty()) return emptyList()
    val byFolder = videos.groupBy { it.folderGroup }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val out = ArrayList<LibraryListItem>()
    for ((folder, list) in byFolder) {
        out.add(LibraryListItem.Header(folder))
        list.sortedBy { it.title.lowercase() }.forEach { out.add(LibraryListItem.VideoRow(it)) }
    }
    return out
}

fun playlistIdsInOrder(items: List<LibraryListItem>): LongArray {
    return items.filterIsInstance<LibraryListItem.VideoRow>().map { it.entity.id }.toLongArray()
}
