package cloud.operon.platform.service.util;

import cloud.operon.platform.service.OperinoService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterCollector {
    private final Map<String, String> config;
    private final HttpEntity httpEntity;
    private JSONObject postmanConfig;

    public ParameterCollector(Map<String, String> config, HttpEntity httpEntity) {
        this.config = config;
        this.httpEntity = httpEntity;
    }

    public String getWorkspaceMarkdown() throws UnsupportedEncodingException, JSONException {
        return createWorkspaceMarkdown(getPostmanConfig());
    }

    public JSONObject getPostmanConfig() throws UnsupportedEncodingException, JSONException {
        if (null == this.postmanConfig) {
            this.postmanConfig = createPostmanConfig();
        }
        return this.postmanConfig;
    }


    private JSONObject createPostmanConfig() throws JSONException, UnsupportedEncodingException {
        JSONObject json = new JSONObject();
        json.put("id", config.get(OperinoService.DOMAIN));
        json.put("name", config.get(OperinoService.OPERINO_NAME));
        JSONArray values = createPostmanValues();
        json.put("values", values);
        return json;
    }

    private String createWorkspaceMarkdown(JSONObject postmanConfig) throws JSONException, UnsupportedEncodingException {
        JSONArray values = postmanConfig.getJSONArray("values");

        return "# Ehrscape Domain provisioning\n"
            .concat("\n")
            .concat("## Domain login details\n")
            .concat("\n")
            .concat("openEhrApi:").concat(findValue(values, "openEhrApi")).concat("\n")
            .concat("domainName:").concat(findValue(values, "domainName")).concat("\n")
            .concat("domainSuffix:").concat(findValue(values, "domainSuffix")).concat("\n")
            .concat("CDRName:").concat(findValue(values, "CDRName")).concat("\n")
            .concat("SessionHeader:").concat(findValue(values, "SessionHeader")).concat("\n")
            .concat("Username:").concat(findValue(values, "Username")).concat("\n")
            .concat("Password:").concat(findValue(values, "Password")).concat("\n")
            .concat("Authorization:").concat(findValue(values, "Authorization")).concat("\n")
            .concat("accountName:").concat(findValue(values, "accountName")).concat("\n")
            .concat("domainSystemId:").concat(findValue(values, "domainSystemId")).concat("\n")
            .concat("\n")
            .concat("### Dummy patient\n")
            .concat("\n")
            .concat("committerName:").concat(findValue(values, "committerName")).concat("\n")
            .concat("patientName:").concat(findValue(values, "patientName")).concat("\n")
            .concat("subjectId:").concat(findValue(values, "subjectId")).concat("\n")
            .concat("nhsNumber:").concat(findValue(values, "nhsNumber")).concat("\n")
            .concat("subjectNamespace:").concat(findValue(values, "subjectNamespace")).concat("\n")
            .concat("ehrId:").concat(findValue(values, "ehrId")).concat("\n")
            .concat("partyId:").concat(findValue(values, "partyId")).concat("\n")
            .concat("\n")
            .concat("### Sample instance data for dummy patient\n")
            .concat("\n")
            .concat("templateId:").concat(findValue(values, "templateId")).concat("\n")
            .concat("compositionId:").concat(findValue(values, "compositionId")).concat("\n")
            .concat("\n")
            .concat("###Useful links\n")
            .concat("\n")
            .concat("[Ehrscape Explorer](https://test.operon.systems/explorer)\n")
            .concat("[Ehrscape API Explorer](https://test.operon.systems/api-explorer)\n")
            .concat("[Ehrscape API Reference](https://dev.ehrscape.com/documentation.html)\n")
            .concat("[Ehrscape - using Postman](https://github.com/inidus/postman-ehrscape)\n");
    }

    private String findValue(JSONArray array, String key) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            if (key.equals(o.getString("key"))) {
                return o.optString("value", "");
            }
        }
        return "";
    }


    private JSONArray createPostmanValues() throws JSONException, UnsupportedEncodingException {
        JSONArray values = new JSONArray();

        values.put(createMapEntry("openEhrApi", getOpenEhrApiAddress()));
        values.put(createMapEntry("CDRName", getCdrName()));
        values.put(createMapEntry("domainSuffix", getDomainSuffix()));
        values.put(createMapEntry("domainName", config.get(OperinoService.DOMAIN)));
        values.put(createMapEntry("SessionHeader", getSessionHeader()));
        values.put(createMapEntry("Username", config.get(OperinoService.USERNAME)));
        values.put(createMapEntry("Password", config.get(OperinoService.PASSWORD)));
        values.put(createMapEntry("Authorization", config.get(OperinoService.TOKEN)));
        values.put(createMapEntry("accountName", config.get(OperinoService.OPERINO_NAME)));
        values.put(createMapEntry("domainSystemId", getDomainSystemId()));
        values.put(createMapEntry("committerName", getCommitterName()));
        values.put(createMapEntry("patientName", "Ivor Cox"));
        values.put(createMapEntry("subjectId", "9999999000"));
        values.put(createMapEntry("nhsNumber", "9999999000"));
        values.put(createMapEntry("subjectNamespace", "uk.nhs.nhs_number"));
        String ehrId = queryEhrId();
        values.put(createMapEntry("ehrId", ehrId));
        values.put(createMapEntry("partyId", queryPartyId("ivor", "cox")));
        values.put(createMapEntry("templateId", "Vital Signs Encounter (Composition)"));
        values.put(createMapEntry("compositionId", queryCompositionId(ehrId)));

        return values;
    }

    private String getCommitterName() {
        // defaults to 'Dr ' + Account name
        return "Dr " + config.get(OperinoService.OPERINO_NAME);
    }

    private String getDomainSystemId() {
        return config.get(OperinoService.DOMAIN) + "_" + getDomainSuffix();
    }

    private String getOpenEhrApiAddress() {
        // "https://some.domain.name/something/anything" -> "some.domain.name"
        Pattern pattern = Pattern.compile("[^\\/]*\\/\\/([^\\/]*)\\/.*");
        Matcher matcher = pattern.matcher(config.get(OperinoService.BASE_URL));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String getCdrName() {
        return isEhrScape() ? "ehrscape.com" : "ethercis.com";
    }

    private String getSessionHeader() {
        return isEhrScape() ? "Ehr-Session" : "Ehr-Session-disabled";
    }

    private String getDomainSuffix() {
        return isEhrScape() ? "c4h.ehrscape.com" : "something else";
    }

    private boolean isEhrScape() {
        return true;
    }

    private String queryEhrId() throws JSONException {
        String url = config.get(OperinoService.BASE_URL) + "ehr/?subjectId=9999999000&subjectNamespace=uk.nhs.nhs_number";
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.GET, httpEntity, String.class);
        return new JSONObject(result.getBody()).getString("ehrId");
    }

    private String queryPartyId(String firstName, String lastName) throws JSONException {
        String url = config.get(OperinoService.BASE_URL) + "demographics/party/query/?lastNames=*" + lastName + "*&firstNames=*" + firstName + "*";
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.GET, httpEntity, String.class);
        return new JSONObject(result.getBody()).getJSONArray("parties").getJSONObject(0).getString("id");
    }

    private String queryCompositionId(String ehrId) throws JSONException, UnsupportedEncodingException {
        String url = config.get(OperinoService.BASE_URL) + "query";

        String aql = "select c/context/start_time/value as start_time, c/name/value as name, c/uid/value as uid from EHR e [ehr_id/value='" + ehrId + "'] contains COMPOSITION c";
        String aqlRequest = "{\"aql\" : \"" + aql + "\"}";

        HttpEntity<String> postEntity = new HttpEntity<>(aqlRequest, httpEntity.getHeaders());
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.POST, postEntity, String.class);

        return new JSONObject(result.getBody()).getJSONArray("resultSet").getJSONObject(0).getString("uid");
    }

    private JSONObject createMapEntry(String key, String value) throws JSONException {
        JSONObject map = new JSONObject();
        map.put("enabled", true);
        map.put("key", key);
        map.put("value", value);
        map.put("type", "text");
        return map;
    }
}
