package io.github.nnkwrik.goodsservice.service;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AccountServiceTests {
    @Test
    public void addressesValidateRequiredFieldsAndRejectOtherOwners() {
        AccountService.Address address=new AccountService.Address();
        address.setName("测试收货人"); address.setPhone("13800000000");
        address.setRegion("山西省运城市盐湖区"); address.setDetail("测试街道1号");
        AccountService.validateAddress(address);
        address.setPhone("invalid");
        try { AccountService.validateAddress(address); fail("invalid phone"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("电话")); }
        address.setPhone("13800000000");
        AccountService accounts=new AccountService();
        JdbcTemplate db=mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(accounts,"db",db);
        when(db.queryForObject("SELECT COUNT(*) FROM user_address WHERE id=? AND user_id=?",Long.class,1,"intruder")).thenReturn(0L);
        try { accounts.saveAddress("intruder",1,address); fail("must reject other user's address"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("无权")); }
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
