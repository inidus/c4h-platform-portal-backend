package cloud.operon.platform.service.impl;

import cloud.operon.platform.domain.Operino;
import cloud.operon.platform.domain.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import java.text.DateFormat;
import java.util.Date;

public class OperinoProvisionerImplTest {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test to try out if settings for the Provisioner are correct, aka if the platform will connect to the backend when started
     */
    @Test
    public void connectToThinkEhr() {
        OperinoProvisionerImpl impl = new OperinoProvisionerImpl();
        impl.restTemplate = new RestTemplate();
        impl.setDomainUrl("http://127.0.0.1:8080/admin/rest/v1/domains");
        impl.setUsername("admin");
        impl.setPassword("admin");

        impl.afterPropertiesSet();
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
