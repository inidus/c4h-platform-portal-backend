package cloud.c4h.platform.service.util;

import cloud.c4h.platform.domain.Patient;
import cloud.c4h.platform.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A naive rest client for ThinkEhr
 */
@Service
@Transactional
@ConfigurationProperties(prefix = "thinkehr", ignoreUnknownFields = false)
public class ThinkEhrRestClient {

    private final Logger log = LoggerFactory.getLogger(ThinkEhrRestClient.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    String adminName;
    String password;
    String cdrUrl;
    String baseUrl;
    String managerUrl;

    public static String createBasicAuthString(String username, String password) {
        String plainCreds = username + ":" + password;
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        return "Basic " + new String(base64CredsBytes);
    }

    ResponseEntity<Map> doPost(String url, HttpHeaders httpHeaders, Object body) throws JsonProcessingException, RestClientException {

        HttpEntity<Object> request = new HttpEntity<>(objectMapper.writeValueAsString(body), httpHeaders);
        log.debug("request = " + request);

        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, request, Map.class);
        log.debug("responseEntity = {}", responseEntity);

        return responseEntity;
    }

    String queryEhrId() throws JSONException {
        String url = baseUrl + "ehr/?subjectId=9999999000&subjectNamespace=uk.nhs.nhs_number";
        HttpEntity<Object> request = new HttpEntity<>(getAdminHeaders());
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.GET, request, String.class);
        if (result.getStatusCode() == HttpStatus.OK) {
            return new JSONObject(result.getBody()).getString("ehrId");
        } else {
            log.warn("Could not retrieve EHR ID");
            return "n/a";
        }
    }

    String queryPartyId(String firstName, String lastName) throws JSONException {
        String url = baseUrl + "demographics/party/query/?lastNames=*" + lastName + "*&firstNames=*" + firstName + "*";
        HttpEntity<Object> request = new HttpEntity<>(getAdminHeaders());
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.GET, request, String.class);
        if (result.getStatusCode() == HttpStatus.OK) {
            return new JSONObject(result.getBody()).getJSONArray("parties").getJSONObject(0).getString("id");
        } else {
            log.warn("Could not retrieve party ID");
            return "n/a";
        }
    }

    String queryCompositionId(String ehrId) throws JSONException {
        String url = baseUrl + "query";

        String aql = "select c/context/start_time/value as start_time,"
            + " c/name/value as name,"
            + " c/uid/value as uid from EHR e [ehr_id/value='" + ehrId + "'] contains COMPOSITION c";
        String aqlRequest = "{\"aql\" : \"" + aql + "\"}";

        HttpEntity<String> postEntity = new HttpEntity<>(aqlRequest, getAdminHeaders());
        ResponseEntity<String> result = new RestTemplate().exchange(url, HttpMethod.POST, postEntity, String.class);

        if (result.getStatusCode() == HttpStatus.OK) {
            return new JSONObject(result.getBody()).getJSONArray("resultSet").getJSONObject(0).getString("uid");
        } else {
            log.warn("Could not retrieve composition ID");
            return "n/a";
        }
    }

    public ResponseEntity truncateDomain(String domainSystemId) {
        // url is managerUrl+/domain/{{domainSystemId}}/truncate
        HttpEntity<Object> request = new HttpEntity<>(getAdminHeaders());
        log.debug("request = " + request);

        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(managerUrl + "domain/" + domainSystemId + "/truncate", request, Map.class);
        log.debug("responseEntity.getBody() = {}", responseEntity.getBody());
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            log.info("Successfully deleted domain {}", domainSystemId);
        } else {
            log.error("Unable to delete domain {}", domainSystemId);
        }
        return responseEntity;
    }

    public String createPatient(HttpHeaders httpHeaders, Patient patient) throws JsonProcessingException {

        ResponseEntity<Map> responseEntity = doPost(baseUrl + "demographics/party", httpHeaders, transformPatient(patient));
        log.debug("responseEntity = {}", responseEntity);
        log.debug("responseEntity.getBody() = {}", responseEntity.getBody());
        Map response = responseEntity.getBody();
        if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
            Map<String, String> meta = (Map<String, String>) response.get("meta");
            String href = meta.get("href");
            return href.substring(href.lastIndexOf('/') + 1);
        } else {
            throw new RuntimeException("Unable to create patient");
        }
    }

    public String createEhr(Patient patient, HttpHeaders httpHeaders, String subjectNamespace, String subjectId, String committerName) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "ehr")
            .queryParam("subjectNamespace", subjectNamespace)
            .queryParam("subjectId", subjectId)
            .queryParam("committerName", committerName);

        // create patient and then update it - doing a single post to create wont set all additional details
        HttpEntity<Object> request = new HttpEntity<>(httpHeaders);
        log.debug("request = " + request);

        ResponseEntity<Map> responseEntity = restTemplate.exchange(
            builder.build().encode().toUri(),
            HttpMethod.POST,
            request,
            Map.class);
        log.debug("responseEntity = {}", responseEntity);
        log.debug("responseEntity.getBody() = {}", responseEntity.getBody());
        Map response = responseEntity.getBody();
        if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
            String ehrId = response.get("ehrId").toString();
            log.debug("ehrId = {}", ehrId);

            // read body from existing json template and fill in values
            InputStream inputStream = ThinkEhrRestClient.class.getClassLoader().getResourceAsStream("sample_requests/ehrStatusBody.json");

            Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
            String bodyString = s.hasNext() ? s.next() : "";

            // now update data with values from patient
            bodyString = bodyString.replaceAll("<subjectId>", subjectId);
            bodyString = bodyString.replaceAll("<subjectNamespace>", subjectNamespace);
            bodyString = bodyString.replaceAll("<gender>", patient.getGender());
            bodyString = bodyString.replaceAll("<birth_year>", String.valueOf(patient.getDateOfBirth().getYear()));
            request = new HttpEntity<>(bodyString, httpHeaders);
            log.debug("Ehr PUT request = " + request);

            // PUT call to ehr/status/
            builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "ehr/status/" + ehrId);

            responseEntity = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.PUT,
                request,
                Map.class);
            log.debug("EHR PUT responseEntity = {}", responseEntity);
            log.debug("EHR PUT responseEntity.getBody() = {}", responseEntity.getBody());
            response = responseEntity.getBody();
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                Map<String, String> meta = (Map<String, String>) response.get("meta");
                String href = meta.get("href");
                return href.substring(href.lastIndexOf('/') + 1);
            }
        }
        return null;
    }

    public String createComposition(HttpHeaders httpHeaders, String ehrId, String templateId, String commiterName, String compositionPath) throws IOException {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "composition")
            .queryParam("ehrId", ehrId)
            .queryParam("templateId", templateId)
            .queryParam("commiterName", commiterName)
            .queryParam("format", "FLAT");

        Map data = objectMapper.readValue(ThinkEhrRestClient.class.getClassLoader().getResourceAsStream(compositionPath), Map.class);
        HttpEntity<Object> request = new HttpEntity<>(objectMapper.writeValueAsString(data), httpHeaders);
        log.debug("request = " + request);

        ResponseEntity<Map> responseEntity = restTemplate.exchange(
            builder.build().encode().toUri(),
            HttpMethod.POST,
            request,
            Map.class);
        log.debug("responseEntity = {}", responseEntity);
        log.debug("responseEntity.getBody() = {}", responseEntity.getBody());
        Map response = responseEntity.getBody();
        if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
            return response.get("compositionUid").toString();
        } else {
            throw new RuntimeException("Unable to create composition");
        }
    }

    public void uploadTemplate(HttpHeaders httpHeaders, String templatePath) {
        httpHeaders.setContentType(MediaType.APPLICATION_XML);

        String templateToSubmit = null;
        try (InputStream inputStream = ThinkEhrRestClient.class.getClassLoader().getResourceAsStream(templatePath)) {
            BufferedReader bufReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            String line = bufReader.readLine();
            while (line != null) {
                sb.append(line).append("\n");
                line = bufReader.readLine();
            }
            // now set final value of templateToSubmit
            templateToSubmit = sb.toString();

            HttpEntity<String> templateRequest = new HttpEntity<>(templateToSubmit, httpHeaders);
            log.trace("templateRequest = " + templateRequest);
            ResponseEntity templateResponse = restTemplate.postForEntity(baseUrl + "template", templateRequest, String.class);
            log.trace("templateResponse = " + templateResponse);

        } catch (NullPointerException | IOException e) {
            log.error("Unable to read init template from class path. Nested exception is : ", e);
        } finally {
            log.trace("Init template : {}", templateToSubmit);
        }
    }


    public ResponseEntity<String> createDomain(String domainName, String projectName) throws URISyntaxException {
        URI uri = new URI(cdrUrl + "/admin/rest/v1/domains");
        String requestJson = "{" +
            "\"blocked\": \"false\"," +
            "\"description\": \"" + projectName + "\"," +
            "\"name\": \"" + domainName + "\"," +
            "\"systemId\": \"" + domainName + "\"" +
            "}";
        HttpEntity<Object> request = new HttpEntity<>(requestJson, getAdminHeaders());
        log.debug("createDomain" + requestJson);
        return restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
    }

    public ResponseEntity<String> createUser(String domainName, User domainUser, String domainPassword) throws URISyntaxException {
        URI uri = new URI(cdrUrl + "/admin/rest/v1/users");

        String requestJson = "{" +
            "\"username\": \"" + domainName + "\"," +
            "\"password\": \"" + domainPassword + "\"," +
            "\"name\": \"" + domainUser.getLogin() + "\"," +
            "\"externalRef\": null," +
            "\"blocked\": false," +
            "\"defaultDomain\": \"" + domainName + "\"," +
            "\"roles\": {\"" + domainName + "\": [\"ROLE_ADMIN\"]}," +
            "\"superUser\": false" +
            "}";
        HttpEntity<Object> request = new HttpEntity<>(requestJson, getAdminHeaders());

        return restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
    }

    /**
     * Utility method for wrapping {@link Patient} object in the format that ThinkEhr expects
     */
    private Map<String, Object> transformPatient(Patient patient) {
        Map<String, Object> map = new HashMap<>();
        map.put("dateOfBirth", patient.getDateOfBirth());
        map.put("firstNames", patient.getForename());
        map.put("gender", patient.getGender().toUpperCase());
        map.put("lastNames", patient.getSurname());
        map.put("version", 1);
        // create address key
        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("address", patient.getAddress1().concat(" ").concat(patient.getAddress2()).concat(" ").concat(patient.getAddress3()));
        addressMap.put("version", 1);
        map.put("address", addressMap);

        // create partyAdditionalInfo
        Map<String, Object> titleMap = new HashMap<>();
        titleMap.put("key", "title");
        titleMap.put("value", patient.getTitle());
        titleMap.put("version", 1);
        Map<String, Object> nhsNumberMap = new HashMap<>();
        nhsNumberMap.put("key", "uk.nhs.nhs_number");
        nhsNumberMap.put("value", patient.getNhsNumber());
        nhsNumberMap.put("version", 1);
        // add title and nhs number maps to partyAdditionalInfo
        List<Map<String, Object>> partyAdditionalInfo = new ArrayList<>();
        partyAdditionalInfo.add(titleMap);
        partyAdditionalInfo.add(nhsNumberMap);

        map.put("partyAdditionalInfo", partyAdditionalInfo);

        return map;
    }

    private HttpHeaders getAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String base64Creds = createBasicAuthString(this.adminName, this.password);
        headers.add("Authorization", base64Creds);

        return headers;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    public void setManagerUrl(String managerUrl) {
        this.managerUrl = managerUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setCdrUrl(String cdrUrl) {
        this.cdrUrl = cdrUrl;
    }
}
