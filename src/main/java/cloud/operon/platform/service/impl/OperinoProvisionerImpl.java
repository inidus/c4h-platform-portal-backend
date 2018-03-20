package cloud.operon.platform.service.impl;

import cloud.operon.platform.domain.Operino;
import cloud.operon.platform.domain.Patient;
import cloud.operon.platform.service.MailService;
import cloud.operon.platform.service.OperinoProvisioner;
import cloud.operon.platform.service.OperinoService;
import cloud.operon.platform.service.util.ParameterCollector;
import cloud.operon.platform.service.util.ThinkEhrRestClient;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service Implementation for provisioning Operinos.
 */
@Service
@Transactional
@RabbitListener(queues = "operinos")
@ConfigurationProperties(prefix = "provisioner", ignoreUnknownFields = false)
public class OperinoProvisionerImpl implements InitializingBean, OperinoProvisioner {
    /**
     * We use the same password for every domain
     */
    private static final String DOMAIN_PASSWORD = "$2a$10$619ki";
    private final Logger log = LoggerFactory.getLogger(OperinoProvisionerImpl.class);
    private final List<Patient> patients;
    String subjectNamespace;
    String agentName;

    @Autowired
    OperinoService operinoService;
    @Autowired
    ThinkEhrRestClient thinkEhrRestClient;
    @Autowired
    MailService mailService;

    OperinoProvisionerImpl() {
        patients = new ArrayList<>();
        // load patients from files
        for (int i = 0; i < 2; i++) {
            patients.addAll(loadPatientsList("data/patients" + (i + 1) + ".csv"));
            log.debug("Loaded {} patients from file {}", patients.size(), "data/patients" + i + ".csv");
        }
        log.debug("Final number of patients = {}", patients.size());
    }

    @Override
    @RabbitHandler
    public void receive(@Payload Operino project) {
        log.debug("Receiving Project " + project.toString());
        try {
            provision(project);

            sendConfirmationEmail(project);
        } catch (URISyntaxException e) {
            log.warn("Could not provision", e);
        } catch (RestClientException e) {
            log.warn("Problem provisioning", e);
        }
    }

    private void sendConfirmationEmail(@Payload Operino project) {
        Map<String, String> config = operinoService.getConfigForOperino(project);

        try {
            ParameterCollector parameterCollector = new ParameterCollector(thinkEhrRestClient, config);

            JSONObject pm = parameterCollector.getPostmanConfig();
            ByteArrayResource postman = new ByteArrayResource(pm.toString().getBytes());

            String md = parameterCollector.getWorkspaceMarkdown();
            ByteArrayResource markdown = new ByteArrayResource(md.getBytes());

            mailService.sendProvisioningCompletionEmail(project, config, postman, markdown);
        } catch (JSONException e) {
            log.warn("Could not create attachments");
            mailService.sendProvisioningCompletionEmail(project, config, null, null);
        }
    }

    private HttpHeaders provision(Operino project) throws URISyntaxException {
        String domainName = project.getDomain();
        thinkEhrRestClient.createDomain(domainName, project.getName());
        thinkEhrRestClient.createUser(domainName, project.getUser(), DOMAIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", ThinkEhrRestClient.createBasicAuthString(domainName, DOMAIN_PASSWORD));
        // upload various templates - we have to upload at least on template as work around fo EhrExplorer bug
        thinkEhrRestClient.uploadTemplate(headers, "sample_requests/problems/problems-template.xml");
        // now if user has requested provisioning, we upload other templates and generated data
        if (project.getProvision()) {
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/allergies/allergies-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/lab-results/lab-results-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/orders/orders-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/vital-signs/vital-signs-template.xml");
            thinkEhrRestClient.uploadTemplate(headers, "sample_requests/procedures/procedures-template.xml");

            createPatients(headers);
        }

        log.info("Provisioning finished");
        return headers;
    }

    private void createPatients(HttpHeaders headers) {
        log.info("Creating patients (" + patients.size() + ")");
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            for (Patient p : patients) {
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
            }
        } catch (HttpServerErrorException | HttpClientErrorException | IOException e) {
            log.warn("Error creating patient data", e.getMessage());
        }
    }

    @Override
    public void afterPropertiesSet() {
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

    public void setSubjectNamespace(String subjectNamespace) {
        this.subjectNamespace = subjectNamespace;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

}
