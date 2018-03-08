package cloud.operon.platform.service.impl;

import cloud.operon.platform.domain.Operino;
import cloud.operon.platform.domain.Patient;
import cloud.operon.platform.domain.User;
import cloud.operon.platform.service.OperinoProvisioner;
import cloud.operon.platform.service.util.ThinkEhrRestClient;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.*;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Service Implementation for provisioning Operinos.
 */
@Service
@Transactional
@RabbitListener(queues = "operinos")
@ConfigurationProperties(prefix = "provisioner", ignoreUnknownFields = false)
public class OperinoProvisionerImpl implements InitializingBean, OperinoProvisioner {

    private static final String DOMAIN_PASSWORD = "$2a$10$619ki";
    private final Logger log = LoggerFactory.getLogger(OperinoProvisionerImpl.class);
    private final List<Patient> patients = new ArrayList<>();
    String domainUrl;
    String cdrUrl;
    String subjectNamespace;
    String username;
    String password;
    String agentName;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    ThinkEhrRestClient thinkEhrRestClient;

    @Override
    @RabbitHandler
    public void receive(@Payload Operino operino) {
        try {
            provision(operino);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void provision(Operino project) throws URISyntaxException {
        HttpHeaders headers = new HttpHeaders();
        String auth = ThinkEhrRestClient.createBasicAuthString(username, password);
        headers.add("Authorization", "Basic " + auth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response;
        try {
            createDomain(restTemplate, headers, project.getDomain(), project.getName());
        } catch (HttpClientErrorException e) {
        }

        try {
            createUser(restTemplate, headers, project.getDomain(), project.getUser());
        } catch (HttpClientErrorException e) {
        }

        headers.setContentType(MediaType.APPLICATION_XML);
        // upload various templates - we have to upload at least on template as work around fo EhrExplorer bug
        thinkEhrRestClient.uploadTemplate(headers, "sample_requests/problems/problems-template.xml");
        // now if user has requested provisioning, we upload other templates and generated data
        if (project.getProvision()) {
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/allergies/allergies-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/lab-results/lab-results-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/orders/orders-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/vital-signs/vital-signs-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/procedures/procedures-template.xml");
        }

        if (project.getProvision()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            createPatients(headers);
        }

    }

    private ResponseEntity<String> createDomain(RestTemplate restTemplate, HttpHeaders headers, String domainName, String projectName) throws URISyntaxException {
        URI uri = new URI(cdrUrl + "/admin/rest/v1/domains");
        String requestJson = "{" +
            "\"blocked\": \"false\"," +
            "\"description\": \"" + projectName + "\"," +
            "\"name\": \"" + domainName + "\"," +
            "\"systemId\": \"" + domainName + "\"" +
            "}";
        HttpEntity<Object> request = new HttpEntity<>(requestJson, headers);

        return restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
    }

    private ResponseEntity<String> createUser(RestTemplate restTemplate, HttpHeaders headers, String domainName, User domainUser) throws URISyntaxException {
        URI uri = new URI(cdrUrl + "/admin/rest/v1/users");

        String requestJson = "{" +
            "\"username\": \"" + domainName + "\"," +
            "\"password\": \"" + DOMAIN_PASSWORD + "\"," +
            "\"name\": \"" + domainUser.getLogin() + "\"," +
            "\"externalRef\": null," +
            "\"blocked\": false," +
            "\"defaultDomain\": \"" + domainName + "\"," +
            "\"roles\": {\"" + domainName + "\": [\"ROLE_ADMIN\"]}," +
            "\"superUser\": false" +
            "}";
        HttpEntity<Object> request = new HttpEntity<>(requestJson, headers);

        return restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
    }

    private void createPatients(HttpHeaders headers) {
        log.info("Creating patients (" + patients.size() + ")");
        for (Patient p : patients) {
            try {
                // create patient
                String patientId = thinkEhrRestClient.createPatient(headers, p);
                log.debug("Created patient with Id = {}", patientId);
                // create ehr
                String ehrId = thinkEhrRestClient.createEhr(p, headers, subjectNamespace, p.getNhsNumber(), agentName);
                log.debug("Created ehr with Id = {}", ehrId);
                // now upload compositions against each template loaded above
                // -- first process vital signs template compositions
                // create composition file path
                String compositionPath = "sample_requests/vital-signs/vital-signs-composition.json";
                String compositionId = thinkEhrRestClient.createComposition(headers, ehrId, "Vital Signs Encounter (Composition)", agentName, compositionPath);
                log.debug("Created composition with Id = {}", compositionId);
                // -- first process allergy template compositions
                for (int i = 1; i < 7; i++) {
                    // create composition file path
                    compositionPath = "sample_requests/allergies/AllergiesList_" + i + "FLAT.json";
                    compositionId = thinkEhrRestClient.createComposition(headers, ehrId, "IDCR Allergies List.v0", agentName, compositionPath);
                    log.debug("Created composition with Id = {}", compositionId);
                }
                // -- next process lab order compositions
                for (int i = 1; i < 13; i++) {
                    // create composition file path
                    compositionPath = "sample_requests/orders/IDCR_Lab_Order_FLAT_" + i + ".json";
                    compositionId = thinkEhrRestClient.createComposition(headers, ehrId, "IDCR - Laboratory Order.v0", agentName, compositionPath);
                    log.debug("Created composition with Id = {}", compositionId);
                }
                // -- next process procedure compositions
                for (int i = 1; i < 7; i++) {
                    // create composition file path
                    compositionPath = "sample_requests/procedures/IDCR_Procedures_List_FLAT_" + i + ".json";
                    compositionId = thinkEhrRestClient.createComposition(headers, ehrId, "IDCR Procedures List.v0", agentName, compositionPath);
                    log.debug("Created composition with Id = {}", compositionId);
                }
                // -- next process lab result compositions
                for (int i = 1; i < 13; i++) {
                    // create composition file path
                    compositionPath = "sample_requests/lab-results/IDCR_Lab_Report_INPUT_FLAT_" + i + ".json";
                    compositionId = thinkEhrRestClient.createComposition(headers, ehrId, "IDCR - Laboratory Test Report.v0", agentName, compositionPath);
                    log.debug("Created composition with Id = {}", compositionId);
                }

            } catch (HttpServerErrorException | HttpClientErrorException | IOException e) {
                log.warn("Error creating patient data", e.getMessage());
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + ThinkEhrRestClient.createBasicAuthString(username, password));
        headers.setContentType(MediaType.APPLICATION_JSON);

        // connect to api
        HttpEntity<Map<String, String>> getRequest = new HttpEntity<>(headers);
        log.debug("getRequest = " + getRequest);
        ResponseEntity<List> getResponse;
        try {
            getResponse = restTemplate.exchange(domainUrl, HttpMethod.GET, getRequest, List.class);

            log.debug("getResponse = " + getResponse);
            if (getResponse == null || getResponse.getStatusCode() != HttpStatus.OK) {
                log.error("Unable to connect to ThinkEHR backend specified by: " + domainUrl);
            } else {
                // load patients from files
                for (int i = 0; i < 5; i++) {
                    patients.addAll(loadPatientsList("data/patients" + (i + 1) + ".csv"));
                    log.info("Loaded {} patients from file {}", patients.size(), "data/patients" + i + ".csv");
                }
                log.info("Final number of patients = {}", patients.size());
            }
        } catch (RestClientException e) {
            log.error("Unable to connect to ThinkEHR backend specified by: " + domainUrl);
        }
    }

    private List<Patient> loadPatientsList(String fileName) {
        try {
            CsvSchema bootstrapSchema = CsvSchema.emptySchema().withHeader();
            CsvMapper mapper = new CsvMapper();
            MappingIterator<Patient> mappingIterator = mapper.reader(Patient.class).with(bootstrapSchema).readValues(OperinoProvisionerImpl.class.getClassLoader().getResourceAsStream(fileName));
            return mappingIterator.readAll();
        } catch (Exception e) {
            log.error("Error occurred while loading object list from file " + fileName, e);
            return Collections.emptyList();
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setCdrUrl(String cdrUrl) {
        this.cdrUrl = cdrUrl;
    }

    public void setDomainUrl(String domainUrl) {
        this.domainUrl = domainUrl;
    }

    public void setSubjectNamespace(String subjectNamespace) {
        this.subjectNamespace = subjectNamespace;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

}
