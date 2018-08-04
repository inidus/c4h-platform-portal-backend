package cloud.c4h.platform.service.impl;

import cloud.c4h.platform.domain.Notification;
import cloud.c4h.platform.domain.enumeration.NotificationStatus;
import cloud.c4h.platform.repository.NotificationRepository;
import cloud.c4h.platform.service.MailService;
import cloud.c4h.platform.service.OperinoConfiguration;
import cloud.c4h.platform.service.OperinoService;
import cloud.c4h.platform.service.util.PdfReportGenerator;
import cloud.c4h.platform.service.util.ThinkEhrRestClient;
import com.lowagie.text.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Service Implementation for processing notifications.
 */
@Service
@Transactional
//@RabbitListener(queues = "notifications")
@RabbitListener(bindings = @QueueBinding(value = @Queue(value = "notifications", durable = "true"), exchange = @Exchange(value = "exch", autoDelete = "true"), key = "key"))
@ConfigurationProperties(prefix = "notifier", ignoreUnknownFields = false)
public class NotificationProcessorImpl {

    private final Logger log = LoggerFactory.getLogger(NotificationProcessorImpl.class);
    String openEhrUrl;
    String teamName;
    boolean skipCompositionIdValidation;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OperinoService operinoService;

    @Autowired
    private OperinoConfiguration operinoConfigService;

    @Autowired
    private MailService mailService;

    //    @RabbitListener(queues = "notifications")
    @RabbitHandler
    public void receive(@Payload Notification notification) {
        log.debug("Received notification {}", notification);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (!skipCompositionIdValidation) {
            Map<String, String> config = operinoConfigService.getConfigForOperino(notification.getOperino());
            String user = config.get(OperinoService.USERNAME);
            String pass = config.get(OperinoService.PASSWORD);

            String base64Creds = ThinkEhrRestClient.createBasicAuthString(user, pass);
            headers.add("Authorization", base64Creds);
        }

        HttpEntity<Map<String, String>> getRequst = new HttpEntity<>(headers);
        log.debug("getRequest = " + getRequst);
        try {
            // create input stream with pdf as content
            String reportFileName = UUID.randomUUID().toString();
            String reportPath = PdfReportGenerator.createPdf(reportFileName, notification.getFormData());
            ResponseEntity<Resource> getResponse = null;
            if (!skipCompositionIdValidation) {
                getResponse = restTemplate.exchange(openEhrUrl + notification.getRecordComponentId(), HttpMethod.GET, getRequst, Resource.class);
                log.debug("getResponse = " + getResponse);
            }
            // now process notification and send emails
            if ((getResponse != null && getResponse.getStatusCode() == HttpStatus.OK) || skipCompositionIdValidation) {
                // now loop though recipients and send emails to all
                notification.getEmail().getRecipients().forEach(recipient -> {
                    mailService.sendEmailWithAttachment(recipient, notification.getEmail().getReportEmail().getSubject(),
                        notification.getEmail().getReportEmail().getBody(),
                        reportFileName + ".pdf", reportPath, "application/pdf", true, true);
                    log.info("Sent report to recipient = {}", recipient);
                });

                // now loop through confirmation receivers and notify all
                notification.getEmail().getConfirmationReceivers().forEach(recipient -> {
                    mailService.sendEmail(recipient, notification.getEmail().getConfirmationEmail().getSubject(),
                        notification.getEmail().getConfirmationEmail().getBody(), true, true);
                    log.info("Sent confirmation to recipient = {}", recipient);
                });
                // update notification status
                notification.setStatus(NotificationStatus.SENT);

            } else {
                // update notification status
                notification.setStatus(NotificationStatus.FAILED);
                log.error("Unable to verify access composition with id {}.", notification.getRecordComponentId());
            }
        } catch (HttpClientErrorException e) {
            // update notification status
            notification.setStatus(NotificationStatus.FAILED);
            log.error("Error accessing openEhrUrl for notification {}. Nested exception is : {}", notification, e);
        } catch (IOException e) {
            // update notification status
            notification.setStatus(NotificationStatus.FAILED);
            log.error("Error reading response from rest call. Nested exception is : ", e);
        } catch (DocumentException e) {
            log.error("Error generating pdf from rest call. Nested exception is : ", e);
        }

        // save notification
//        notificationRepository.save(notification);
    }

    public void setOpenEhrUrl(String openEhrUrl) {
        this.openEhrUrl = openEhrUrl;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public void setNotificationRepository(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void setSkipCompositionIdValidation(boolean skipCompositionIdValidation) {
        this.skipCompositionIdValidation = skipCompositionIdValidation;
    }
}
