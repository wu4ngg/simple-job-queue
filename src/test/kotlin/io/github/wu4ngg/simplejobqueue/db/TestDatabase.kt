package io.github.wu4ngg.simplejobqueue.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.postgresql.PostgreSQLContainer

object TestDatabase {
    private val externalUrl = System.getenv("TEST_DB_URL")
    private val container = if (externalUrl != null) null else PostgreSQLContainer("postgres:17").apply { start() }
    val dbUrl: String = externalUrl ?: container!!.jdbcUrl
    val dbUsername: String = if (externalUrl != null)
        requireNotNull(System.getenv("TEST_DB_USER")) {
            "TEST_DB_USER must be set when TEST_DB_URL is set"
        } else container!!.username
    val dbPassword: String = if (externalUrl != null)
        requireNotNull(System.getenv("TEST_DB_PASSWORD")) {
            "TEST_DB_PASSWORD must be set when TEST_DB_URL is set"
        } else container!!.password
    private val config = HikariConfig().apply {
        this.jdbcUrl = dbUrl
        this.username = dbUsername
        this.password = dbPassword
        maximumPoolSize = 10
    }
    val ds = HikariDataSource(config).also { migrate(it) }
    fun reset() {
        val sql = "TRUNCATE deliveries, endpoints"
        ds.connection.use { connection -> connection.prepareStatement(sql).use { ps -> ps.execute() } }
    }
}
