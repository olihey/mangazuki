package com.mangaread.core.data

import app.cash.sqldelight.db.SqlDriver
import com.mangaread.core.data.db.MangaDatabase

fun createMangaDatabase(driver: SqlDriver): MangaDatabase = MangaDatabase(driver)
