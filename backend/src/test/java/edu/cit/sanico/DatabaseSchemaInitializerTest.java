package edu.cit.sanico;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;

@SpringBootTest
@Disabled("Manual utility to apply schema to Supabase")
class DatabaseSchemaInitializerTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void applySchema() throws Exception {
        File schemaFile = new File("../db/schema.sql");
        if (!schemaFile.exists()) {
            schemaFile = new File("db/schema.sql");
        }
        System.out.println("Applying schema from: " + schemaFile.getAbsolutePath());
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new FileSystemResource(schemaFile));
            System.out.println("Schema successfully applied to Supabase!");
        }
    }
}
