package cloud.operon.platform.service.util;

import cloud.operon.platform.service.OperinoService;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

public class ParameterCollectorTest {

    private ParameterCollector impl;

    @Before
    public void setup() {
        // create Map of data to be posted for domain creation
        HashMap<String, String> data = new HashMap<>();
        data.put(OperinoService.DOMAIN, "c578c3c4-5bed-4143-b3cb-2a046116e65a");
        data.put(OperinoService.DOMAIN_SYSTEM_ID, "c578c3c4-5bed-4143-b3cb-2a046116e65a");
        data.put(OperinoService.USER_DISPLAY_NAME_OR_DOMAIN, "Test User");
        data.put(OperinoService.USERNAME, "oprn_hcbox");
        data.put(OperinoService.PASSWORD, "XioTAJoO479");

        String base64Creds = "b3Bybl90cmFpbmluZzpHaXlUQVphRTEyMQ==";

        data.put(OperinoService.TOKEN, base64Creds);
        data.put(OperinoService.BASE_URL, "https://test.operon.systems/rest/v1/");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Ehr-Session-disabled", "0ce5ec82-3954-4388-bfd7-e48f6db613e8");
        HttpEntity<Map<String, String>> getRequest = new HttpEntity<>(headers);

        impl = new ParameterCollector(data, getRequest);
    }


    @Test
    public void createPostmanConfig() throws Exception {
        JSONObject postmanConfig = impl.getPostmanConfig();
    }

    @Test
    public void createMarkdown() throws Exception {
        String markdown = impl.getWorkspaceMarkdown();
        System.out.print(markdown);
    }

}
