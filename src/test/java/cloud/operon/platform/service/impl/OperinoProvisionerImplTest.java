package cloud.operon.platform.service.impl;

import cloud.operon.platform.domain.Operino;
import cloud.operon.platform.domain.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.DateFormat;
import java.util.Date;

public class OperinoProvisionerImplTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void receive() {
        String testString = "OperinoProvisionerImplTest " + DateFormat.getTimeInstance().format(new Date());

        Operino operino = new Operino();
        User user = new User();
        user.setLogin("user " + testString);
        user.setPassword("pass " + testString);
        operino.setUser(user);
        operino.setName(testString);

        OperinoProvisionerImpl impl = new OperinoProvisionerImpl();
        impl.setCdrUrl("http://127.0.0.1:8080");
        impl.setUsername("admin");
        impl.setPassword("admin");
        impl.receive(operino);
    }
}
