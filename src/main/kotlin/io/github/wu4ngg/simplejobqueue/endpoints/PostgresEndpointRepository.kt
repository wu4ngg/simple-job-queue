package io.github.wu4ngg.simplejobqueue.endpoints

import io.github.wu4ngg.simplejobqueue.models.Endpoint
import io.github.wu4ngg.simplejobqueue.models.Secret
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource


class PostgresEndpointRepository(val ds: DataSource) : EndpointRepository {
    private suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) { ds.connection.use(block) }

    private fun ResultSet.toEndpoint() = Endpoint(
        id = getObject("id", UUID::class.java).toString(),
        url = getString("url"),
        secret = Secret(getString("secret")),
    )

    override suspend fun create(url: String): Endpoint {
        val secret = generateSecret()
        val uuid = UUID.randomUUID()
        val insertSQL = """
            INSERT INTO endpoints (id, url, secret) VALUES (
                ?, ?, ?
            )
        """.trimIndent()
        val res = withConnection { connection ->
            connection.prepareStatement(insertSQL).use { st ->
                st.setObject(1, uuid)
                st.setString(2, url)
                st.setString(3, secret.value)
                st.executeUpdate()
            }
        }
        return Endpoint(uuid.toString(), url, secret)
    }

    override suspend fun get(id: String): Endpoint? {
        val getSQL = """
            SELECT id, url, secret
            FROM endpoints
            WHERE id = ?
        """.trimIndent()
        val res = withConnection { connection ->
            connection.prepareStatement(getSQL).use { ps ->
                ps.setObject(1, UUID.fromString(id))
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toEndpoint() else null
                }
            }
        }
        return res
    }
}