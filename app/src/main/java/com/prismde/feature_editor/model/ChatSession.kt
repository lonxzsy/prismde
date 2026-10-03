package com.prismde.feature_editor.model

import com.prismde.feature_build.engine.AgentChatMessage
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<AgentChatMessage> = emptyList()
)
