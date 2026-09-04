package io.github.shaigrorb.mylifeuptoolkit

import java.util.UUID

data class TaskShortcut(
    val label: String,
    val count: Int,
    val id: String = UUID.randomUUID().toString()
)
