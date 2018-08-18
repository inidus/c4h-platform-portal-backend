package cloud.c4h.platform.service.util;

import cloud.c4h.platform.service.OperinoService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterCollector {
    private final ThinkEhrRestClient thinkEhrRestClient;
    private final Map<String, String> config;
    private JSONObject postmanConfig;

    public ParameterCollector(ThinkEhrRestClient thinkEhrRestClient, Map<String, String> config) {
        this.thinkEhrRestClient = thinkEhrRestClient;
        this.config = config;
    }

    public String getWorkspaceMarkdown() throws JSONException {
        return createWorkspaceMarkdown(getPostmanConfig());
    }

    public JSONObject getPostmanConfig() throws JSONException {
        if (null == this.postmanConfig) {
            this.postmanConfig = createPostmanConfig();
        }
        return this.postmanConfig;
    }

    private JSONObject createPostmanConfig() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", config.get(OperinoService.DOMAIN));
        json.put("name", config.get(OperinoService.OPERINO_NAME));
        JSONArray values = createPostmanValues();
        json.put("values", values);
        return json;
    }

    private JSONArray createPostmanValues() throws JSONException {

        String user = config.get(OperinoService.USERNAME);
        String pass = config.get(OperinoService.PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", ThinkEhrRestClient.createBasicAuthString(user, pass));
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");

        String ehrId = thinkEhrRestClient.queryEhrId(headers);
        return new JSONArray()
            .put(createMapEntry("openEhrApi", config.get(OperinoService.CDR)))
            .put(createMapEntry("openEhrExplorer", config.get(OperinoService.EXPLORER)))
            .put(createMapEntry("CDRName", getCdrName()))
            .put(createMapEntry("domainSuffix", getDomainSuffix()))
            .put(createMapEntry("domainName", config.get(OperinoService.DOMAIN)))
            .put(createMapEntry("SessionHeader", getSessionHeader()))
            .put(createMapEntry("Username", user))
            .put(createMapEntry("Password", pass))
            .put(createMapEntry("Authorization", ThinkEhrRestClient.createBasicAuthString(user, pass)))
            .put(createMapEntry("accountName", config.get(OperinoService.OPERINO_NAME)))
            .put(createMapEntry("domainSystemId", getDomainSystemId()))
            .put(createMapEntry("committerName", getCommitterName()))
            .put(createMapEntry("patientName", "Ivor Cox"))
            .put(createMapEntry("subjectId", "9999999000"))
            .put(createMapEntry("nhsNumber", "9999999000"))
            .put(createMapEntry("subjectNamespace", "uk.nhs.nhs_number"))
            .put(createMapEntry("ehrId", ehrId))
            .put(createMapEntry("partyId", thinkEhrRestClient.queryPartyId(headers,"ivor", "cox")))
            .put(createMapEntry("templateId", "Vital Signs Encounter (Composition)"))
            .put(createMapEntry("compositionId", thinkEhrRestClient.queryCompositionId(headers,ehrId)));
    }

    private String createWorkspaceMarkdown(JSONObject postmanConfig) throws JSONException {
        JSONArray values = postmanConfig.getJSONArray("values");

        return ("# " + getMarkdownHeading() + "\n"
            + "\n"
            + "## Domain login details\n"
            + "\n"
            + "openEhrApi:" + findValue(values, "openEhrApi") + "\n"
            + "openEhrExplorer:" + findValue(values, "openEhrExplorer") + "\n"
            + "domainName:" + findValue(values, "domainName") + "\n"
            + "domainSuffix:" + findValue(values, "domainSuffix") + "\n"
            + "CDRName:" + findValue(values, "CDRName") + "\n"
            + "SessionHeader:" + findValue(values, "SessionHeader") + "\n"
            + "Username:" + findValue(values, "Username") + "\n"
            + "Password:" + findValue(values, "Password") + "\n"
            + "Authorization:" + findValue(values, "Authorization") + "\n"
            + "accountName:" + findValue(values, "accountName") + "\n"
            + "domainSystemId:" + findValue(values, "domainSystemId") + "\n"
            + "\n"
            + "### Dummy patient\n"
            + "\n"
            + "committerName:" + findValue(values, "committerName") + "\n"
            + "patientName:" + findValue(values, "patientName") + "\n"
            + "subjectId:" + findValue(values, "subjectId") + "\n"
            + "nhsNumber:" + findValue(values, "nhsNumber") + "\n"
            + "subjectNamespace:" + findValue(values, "subjectNamespace") + "\n"
            + "ehrId:" + findValue(values, "ehrId") + "\n"
            + "partyId:" + findValue(values, "partyId") + "\n"
            + "\n"
            + "### Sample instance data for dummy patient\n"
            + "\n"
            + "templateId:" + findValue(values, "templateId") + "\n"
            + "compositionId:" + findValue(values, "compositionId") + "\n"
            + "\n"
            + "### Useful links\n"
            + "\n"
            + getMarkdownFooter());
    }

    private String getMarkdownHeading() {
        if (isCode4Health()) {
            return "Code4Health Project Provisioning";
        } else {
            return "inidus Cloud Project Provisioning";
        }
    }

    private String getMarkdownFooter() {
        if (isCode4Health()) {
            return "[CDR Explorer](+)\n"
                + "[CDR API Explorer](https://cdr.code4health.org/api-explorer)\n"
                + "[CDR API Reference](https://dev.ehrscape.com/documentation.html)\n"
                + "[Ehrscape - using Postman](https://docs.code4health.org/ES0-overview-openehr-ehrscape.html)";
        } else {
            return "[CDR Explorer](https://cdr.inidus.cloud/explorer)\n" +
                "[CDR API Explorer]( https://cdr.inidus.cloud/api-explorer)\n" +
                "[CDR API Reference](https://dev.ehrscape.com/documentation.html)\n" +
                "[Ehrscape - using Postman](https://docs.code4health.org/ES0-overview-openehr-ehrscape.html)";
        }
    }

    private String getDomainSuffix() {
        return isCode4Health() ? "code4health.org" : "inidus.cloud";
    }

    private boolean isCode4Health() {
        return true;
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

    private String getCommitterName() {
        // defaults to 'Dr ' + Account name
        return "Dr " + config.get(OperinoService.USER_DISPLAY_NAME_OR_DOMAIN);
    }

    private String getDomainSystemId() {
        return config.get(OperinoService.DOMAIN) + "_" + getDomainSuffix();
    }

    private String getCdrName() {
        return isEhrScape() ? "ehrscape.com" : "ethercis.com";
    }

    private String getSessionHeader() {
        return isEhrScape() ? "Ehr-Session-disabled" : "Ehr-Session";
    }

    private boolean isEhrScape() {
        return true;
    }

    private JSONObject createMapEntry(String key, String value) throws JSONException {
        JSONObject map = new JSONObject();
        map.put("key", key);
        map.put("value", value);
        map.put("type", "text");
        map.put("enabled", true);
        return map;
    }
}
