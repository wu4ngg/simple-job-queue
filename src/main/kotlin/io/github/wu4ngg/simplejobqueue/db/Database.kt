package io.github.wu4ngg.simplejobqueue.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun Application.configureDatabase(): DataSource {
    val db = environment.config.config("database")
    val dbUrl = db.property("url").getString()
    val dbUser = db.property("user").getString()
    val dbPassword = db.property("password").getString()
    val dbMaxPoolSize = db.property("maxPoolSize").getString().toIntOrNull()
    val dbConnectionTimeout = db.property("connectionTimeout").getString().toLongOrNull()
    require(dbMaxPoolSize != null && dbMaxPoolSize > 0) { "maxPoolSize must be defined and >0" }
    require(dbConnectionTimeout != null && dbConnectionTimeout > 0) { "connectionTimeout must be defined and >0" }
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = dbUrl
        this.maximumPoolSize = dbMaxPoolSize
        this.username = dbUser
        this.password = dbPassword
        this.poolName = "simplejobqueue"
        this.connectionTimeout = dbConnectionTimeout
    }
    val ds = HikariDataSource(hikariConfig)
    try {
        Flyway.configure().dataSource(ds).load().migrate()
    } catch (ex: Exception) {
        ds.close()
        throw ex
    }
    monitor.subscribe(ApplicationStopping) { log.info("Application stopping") }
    monitor.subscribe(ApplicationStopped) {
        log.info("Closing DB pool")
        ds.close()
    }
    return ds
}
