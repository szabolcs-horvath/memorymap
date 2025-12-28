package com.szabolcshorvath.memorymap.data

import androidx.room.Embedded
import androidx.room.Relation

data class MemoryGroupWithMedia(
    @Embedded val group: MemoryGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val mediaItems: List<MediaItem>
)