/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.template.sql;

import io.flamingock.template.sql.util.SqlStatementParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlStatementParserTest {

    @Test
    void splitStatements_singleStatement() {
        String sql = "CREATE TABLE users (id INT, name VARCHAR(100))";
        List<String> statements = SqlStatementParser.splitStatements(sql);
        assertEquals(1, statements.size());
        assertEquals("CREATE TABLE users (id INT, name VARCHAR(100))", statements.get(0));
    }

    @Test
    void splitStatements_multipleStatements() {
        String sql = "CREATE TABLE users (id INT, name VARCHAR(100)); INSERT INTO users VALUES (1, 'john'); SELECT * FROM users";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(3, statements.size());
        assertEquals("CREATE TABLE users (id INT, name VARCHAR(100))", statements.get(0));
        assertEquals("INSERT INTO users VALUES (1, 'john')", statements.get(1));
        assertEquals("SELECT * FROM users", statements.get(2));
    }

    @Test
    void splitStatements_blockComments() {
        String sql = "/* comment */ CREATE TABLE test (id INT); /* another */ INSERT INTO test VALUES (1)";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0));
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1));
        // Verificar que NO contienen comentarios
        assertFalse(statements.get(0).contains("/*"));
        assertFalse(statements.get(1).contains("/*"));
    }

    @Test
    void splitStatements_lineComments() {
        String sql = "CREATE TABLE test (id INT); -- comment\nINSERT INTO test VALUES (1); -- another";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0));
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1));
        // Verificar que NO contienen comentarios
        assertFalse(statements.get(0).contains("--"));
        assertFalse(statements.get(1).contains("--"));
    }

    @Test
    void splitStatements_mixedComments() {
        String sql = "/* block */ CREATE TABLE test (id INT); -- line\nINSERT INTO test VALUES (1); /* block */";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0));
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1));
        // Verificar que NO contienen comentarios
        assertFalse(statements.stream().anyMatch(s -> s.contains("/*") || s.contains("--")));
    }

    @Test
    void splitStatements_simpleStrings() {
        String sql = "INSERT INTO users (name) VALUES ('john'); INSERT INTO users (name) VALUES ('jane')";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES ('john')", statements.get(0));
        assertEquals("INSERT INTO users (name) VALUES ('jane')", statements.get(1));
    }

    @Test
    void splitStatements_escapedQuotes() {
        String sql = "INSERT INTO users (name) VALUES ('O''Brien'); INSERT INTO users (quote) VALUES ('can''t do it')";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES ('O''Brien')", statements.get(0));
        assertEquals("INSERT INTO users (quote) VALUES ('can''t do it')", statements.get(1));
    }

    @Test
    void splitStatements_doubleQuotes() {
        String sql = "CREATE TABLE \"test table\" (id INT); INSERT INTO \"test table\" VALUES (1)";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE \"test table\" (id INT)", statements.get(0));
        assertEquals("INSERT INTO \"test table\" VALUES (1)", statements.get(1));
    }

    @Test
    void splitStatements_noSemicolon() {
        String sql = "SELECT 1";
        List<String> statements = SqlStatementParser.splitStatements(sql);
        assertEquals(1, statements.size());
        assertEquals("SELECT 1", statements.get(0));
    }

    @Test
    void splitStatements_emptyAndWhitespace() {
        String sql = "   ;   CREATE TABLE test (id INT);   ;   ";
        List<String> statements = SqlStatementParser.splitStatements(sql);
        assertEquals(1, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0));
    }

    @Test
    void splitStatements_complexMultiLine() {
        String sql = "/* Multi-line comment\n" +
                     "   with content */\n" +
                     "CREATE TABLE users (\n" +
                     "    id INT,\n" +
                     "    name VARCHAR(100)\n" +
                     ");\n" +
                     "\n" +
                     "-- Line comment\n" +
                     "INSERT INTO users (id, name) VALUES (1, 'john');\n" +
                     "\n" +
                     "SELECT * FROM users";
        List<String> statements = SqlStatementParser.splitStatements(sql);

        assertEquals(3, statements.size());
        assertEquals("CREATE TABLE users ( id INT, name VARCHAR(100) )", statements.get(0));
        assertEquals("INSERT INTO users (id, name) VALUES (1, 'john')", statements.get(1));
        assertEquals("SELECT * FROM users", statements.get(2));
        // Verificar que NO contienen comentarios
        assertFalse(statements.stream().anyMatch(s -> s.contains("/*") || s.contains("--")));
    }

    @Test
    void getCommand_basicCommands() {
        assertEquals("CREATE", SqlStatementParser.getCommand("CREATE TABLE users"));
        assertEquals("INSERT", SqlStatementParser.getCommand("INSERT INTO users VALUES (1)"));
        assertEquals("SELECT", SqlStatementParser.getCommand("SELECT * FROM users"));
        assertEquals("UPDATE", SqlStatementParser.getCommand("UPDATE users SET name = 'john'"));
        assertEquals("DELETE", SqlStatementParser.getCommand("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void getCommand_edgeCases() {
        assertEquals("UNKNOWN", SqlStatementParser.getCommand(""));
        assertEquals("UNKNOWN", SqlStatementParser.getCommand("   "));
        assertEquals("CREATE", SqlStatementParser.getCommand("  create   table   users"));
    }
}
