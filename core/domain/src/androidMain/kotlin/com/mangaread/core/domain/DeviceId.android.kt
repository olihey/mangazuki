package com.mangaread.core.domain

import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()
