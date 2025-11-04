package com.kaiyikang.winter.jdbc.with.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kaiyikang.winter.context.AnnotationConfigApplicationContext;
import com.kaiyikang.winter.exception.TransactionException;
import com.kaiyikang.winter.jdbc.JdbcTemplate;
import com.kaiyikang.winter.jdbc.JdbcTestBase;

public class JdbcWithTxTest extends JdbcTestBase {

    @Test
    public void testJdbcWithTx() {
        try (var ctx = new AnnotationConfigApplicationContext(JdbcWithTxApplication.class, createPropertyResolver())) {
            JdbcTemplate sql = ctx.getBean(JdbcTemplate.class);
            // Given
            sql.update(CREATE_USER);
            sql.update(CREATE_ADDRESS);
            UserService userService = ctx.getBean(UserService.class);
            AddressService addressService = ctx.getBean(AddressService.class);

            // Check proxy
            assertNotSame(UserService.class, userService.getClass());
            assertNotSame(Address.class, addressService.getClass());

            // proxy object is not inject:
            assertNull(userService.addressService);
            assertNull(addressService.userService);

            User bob = userService.createUser("Bob", 12);
            assertEquals(1, bob.id);

            Address addr1 = new Address(bob.id, "bob address", 200);
            Address addr2 = new Address(bob.id, "bob second address", 201);
            Address addr3 = new Address(bob.id + 1, "not exist user address", 404);

            // Invalid Insert
            assertThrows(TransactionException.class, () -> {
                addressService.addAddress(addr1, addr2, addr3);
            });
            assertTrue(addressService.getAddresses(bob.id).isEmpty());

            // Valid Insert
            addressService.addAddress(addr1, addr2);
            assertEquals(2, addressService.getAddresses(bob.id).size());

            assertThrows(TransactionException.class, () -> {
                userService.deleteUser(bob);
            });

            assertEquals("Bob", userService.getUser(1).name);
            assertEquals(2, addressService.getAddresses(bob.id).size());

        }

        try (var ctx = new AnnotationConfigApplicationContext(JdbcWithTxApplication.class, createPropertyResolver())) {
            AddressService addressService = ctx.getBean(AddressService.class);
            List<Address> addressesOfBob = addressService.getAddresses(1);
            assertEquals(2, addressesOfBob.size());
        }
    }
}
