package com.kaiyikang.winter.jdbc.with.tx;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.Transactional;
import com.kaiyikang.winter.jdbc.JdbcTemplate;
import com.kaiyikang.winter.jdbc.JdbcTestBase;

@Component
@Transactional
public class UserService {
    @Autowired
    AddressService addressService;

    @Autowired
    JdbcTemplate sql;

    public User createUser(String name, int age) {
        Number id = sql.updateAndReturnGeneratedKey(JdbcTestBase.INSERT_USER, name, age);
        User user = new User();
        user.id = id.intValue();
        user.name = name;
        user.theAge = age;
        return user;
    }

    public User getUser(int userId) {
        return sql.queryForObject(JdbcTestBase.SELECT_USER, User.class, userId);
    }

    public void updateUser(User user) {
        sql.update(JdbcTestBase.UPDATE_USER, user.name, user.id);
    }

    public void deleteUser(User user) {
        sql.update(JdbcTestBase.DELETE_USER, user.id);
        addressService.deleteAddress(user.id);
    }
}
