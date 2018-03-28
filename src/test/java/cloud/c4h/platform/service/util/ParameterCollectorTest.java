package cloud.c4h.platform.service.util;

import cloud.c4h.platform.service.OperinoService;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class ParameterCollectorTest {

    private ParameterCollector impl;

    @Before
    public void setup() {
        // create Map of data to be posted for domain creation
        HashMap<String, String> data = new HashMap<>();
        data.put(OperinoService.DOMAIN, "c578c3c4-5bed-4143-b3cb-2a046116e65a");
        data.put(OperinoService.DOMAIN_SYSTEM_ID, "c578c3c4-5bed-4143-b3cb-2a046116e65a");
        data.put(OperinoService.USER_DISPLAY_NAME_OR_DOMAIN, "Test User");
        data.put(OperinoService.USERNAME, "admin");
        data.put(OperinoService.PASSWORD, "admin");
        data.put(OperinoService.BASE_URL, "http://127.0.0.1:8080/rest/v1/");

        ThinkEhrRestClient restClient = new ThinkEhrRestClient();
        restClient.setAdminName("admin");
        restClient.setPassword("admin");
        restClient.setBaseUrl("http://127.0.0.1:8080/rest/v1/");
        impl = new ParameterCollector(restClient, data);
    }


    @Test
    public void createPostmanConfig() throws Exception {
        JSONObject postmanConfig = impl.getPostmanConfig();
        System.out.println(postmanConfig.toString(2));
    }

    @Test
    public void createMarkdown() throws Exception {
        String markdown = impl.getWorkspaceMarkdown();
        System.out.println(markdown);
    }

}
