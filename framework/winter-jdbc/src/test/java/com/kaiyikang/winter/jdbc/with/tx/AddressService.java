package com.kaiyikang.winter.jdbc.with.tx;

import java.util.List;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Transactional;
import com.kaiyikang.winter.jdbc.JdbcTemplate;
import com.kaiyikang.winter.jdbc.JdbcTestBase;

@Component
@Transactional
public class AddressService {
    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate sql;

    public void addAddress(Address... addresses) {
        for (Address address : addresses) {
            userService.getUser(address.userId);
            sql.update(JdbcTestBase.INSERT_ADDRESS, address.userId, address.address, address.zip);
        }
    }

    public List<Address> getAddresses(int userId) {
        return sql.queryForList(JdbcTestBase.SELECT_ADDRESS_BY_USERID, Address.class, userId);
    }

    public void deleteAddress(int userId) {
        sql.update(JdbcTestBase.DELETE_ADDRESS_BY_USERID, userId);
        if (userId == 1) {
            throw new RuntimeException("Rollback delete for user id = 1");
        }
    }
}
