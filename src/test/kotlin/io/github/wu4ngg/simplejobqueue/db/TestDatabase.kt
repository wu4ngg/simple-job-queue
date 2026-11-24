package io.github.wu4ngg.simplejobqueue.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.postgresql.PostgreSQLContainer

object TestDatabase {
    val container = PostgreSQLContainer("postgres:17").apply { start() }
    private val config = HikariConfig().apply {
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
        maximumPoolSize = 10
    }
    val ds = HikariDataSource(config).also { migrate(it) }
    fun reset() {
        val sql = "TRUNCATE deliveries, endpoints"
        ds.connection.use { connection -> connection.prepareStatement(sql).use { ps -> ps.execute() } }
    }
}
