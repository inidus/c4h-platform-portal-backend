package cloud.c4h.platform.service.impl;

import cloud.c4h.platform.OperonCloudPlatformApp;
import cloud.c4h.platform.domain.Operino;
import cloud.c4h.platform.domain.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.DateFormat;
import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OperonCloudPlatformApp.class)
public class OperinoProvisionerImplTest {
    @Autowired()
    OperinoProvisionerImpl impl;

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void receive() {
        String testString = "OperinoProvisionerImplTest " + DateFormat.getDateTimeInstance().format(new Date());

        Operino operino = new Operino();
        User user = new User();
        user.setLogin("user " + testString);
        user.setPassword("pass " + testString);
        operino.setUser(user);
        operino.setName(testString);

        impl.receive(operino);
    }
}
