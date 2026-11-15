package com.plcoding.cardigan.common

import org.springframework.jdbc.core.JdbcTemplate

/** Empties every table in the given schemas. `@SpringBootTest` tests call it in `@BeforeEach`. */
class DatabaseCleaner(
    private val jdbcTemplate: JdbcTemplate,
) {

    fun clean(vararg schemas: String) {
        val placeholders = schemas.joinToString { "?" }
        val tables = jdbcTemplate.queryForList(
            """
            SELECT quote_ident(table_schema) || '.' || quote_ident(table_name)
            FROM information_schema.tables
            WHERE table_schema IN ($placeholders)
            AND table_type = 'BASE TABLE'
            AND table_name <> 'flyway_schema_history'
            """,
            String::class.java,
            *schemas,
        )
        if (tables.isNotEmpty()) {
            jdbcTemplate.execute("TRUNCATE ${tables.joinToString()} CASCADE")
        }
    }
}
