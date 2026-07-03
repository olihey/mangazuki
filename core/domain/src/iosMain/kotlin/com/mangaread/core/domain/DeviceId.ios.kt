package com.mangaread.core.domain

import platform.Foundation.NSUUID

actual fun randomUuid(): String = NSUUID().UUIDString()
