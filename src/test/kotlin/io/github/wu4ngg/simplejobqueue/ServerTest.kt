package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.db.TestDatabase
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {
    @BeforeTest
    fun resetDb() {
        TestDatabase.reset()
    }

    @Test
    fun `root returns 200`() = testApplication {
        // overrides db config with TestDatabase to build without a local db
        environment {
            config = ApplicationConfig("application.conf").mergeWith(
        MapApplicationConfig(
                    "database.url" to TestDatabase.dbUrl,
                    "database.user" to TestDatabase.dbUsername,
                    "database.password" to TestDatabase.dbPassword,
                )
            )
        }
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

}
