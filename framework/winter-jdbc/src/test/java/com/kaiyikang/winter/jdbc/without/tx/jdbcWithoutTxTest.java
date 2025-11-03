package com.kaiyikang.winter.jdbc.without.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.exception.DataAccessException;
import com.kaiyikang.winter.jdbc.JdbcTemplate;
import com.kaiyikang.winter.jdbc.JdbcTestBase;

public class jdbcWithoutTxTest extends JdbcTestBase {

    @Test
    public void testJdbcWithoutTx() {
        try (var ctx = new AnnotationConfigApplicationContext(JdbcWithoutTxApplication.class,
                createPropertyResolver())) {
            JdbcTemplate sql = ctx.getBean(JdbcTemplate.class);
            sql.update(CREATE_USER);
            sql.update(CREATE_ADDRESS);
            int userId1 = sql.updateAndReturnGeneratedKey(INSERT_USER, "Bob", 12).intValue();
            int userId2 = sql.updateAndReturnGeneratedKey(INSERT_USER, "Alice", null).intValue();
            assertEquals(userId1, 1);
            assertEquals(userId2, 2);

            // Query
            User bob = sql.queryForObject(SELECT_USER, User.class, userId1);
            User alice = sql.queryForObject(SELECT_USER, User.class, userId2);
            assertEquals(1, bob.id);
            assertEquals("Bob", bob.name);
            assertEquals(12, bob.theAge);
            assertEquals(2, alice.id);
            assertEquals("Alice", alice.name);
            assertNull(alice.theAge);

            // Query
            assertEquals("Bob", sql.queryForObject(SELECT_USER_NAME, String.class, userId1));
            assertEquals(12, sql.queryForObject(SELECT_USER_AGE, int.class, userId1));

            // Update
            int n1 = sql.update(UPDATE_USER, "Bob Jones", 18, bob.id);
            assertEquals(1, n1);

            // Delete
            int n2 = sql.update(DELETE_USER, alice.id);
            assertEquals(1, n2);
        }

        try (var ctx = new AnnotationConfigApplicationContext(JdbcWithoutTxApplication.class,
                createPropertyResolver())) {
            JdbcTemplate sql = ctx.getBean(JdbcTemplate.class);
            User bob = sql.queryForObject(SELECT_USER, User.class, 1);
            assertEquals("Bob Jones", bob.name);
            assertEquals(18, bob.theAge);
            assertThrows(DataAccessException.class, () -> {
                sql.queryForObject(SELECT_USER, User.class, 2);
            });
        }
    }
}
