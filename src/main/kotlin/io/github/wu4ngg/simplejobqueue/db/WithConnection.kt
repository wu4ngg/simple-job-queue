package io.github.wu4ngg.simplejobqueue.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource
import kotlin.use

suspend fun <T> DataSource.withConnection(block: (Connection) -> T): T =
    withContext(Dispatchers.IO) { connection.use(block) }