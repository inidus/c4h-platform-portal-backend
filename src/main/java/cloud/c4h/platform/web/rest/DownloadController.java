package cloud.c4h.platform.web.rest;

import cloud.c4h.platform.domain.Operino;
import cloud.c4h.platform.security.SecurityUtils;
import cloud.c4h.platform.service.OperinoService;
import cloud.c4h.platform.service.util.ParameterCollector;
import cloud.c4h.platform.service.util.ThinkEhrRestClient;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DownloadController {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    OperinoService projectService;

    @Autowired
    ThinkEhrRestClient ehrClient;

    @GetMapping(value = "/postman/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public HttpEntity<byte[]> downloadPostman(@PathVariable long id) {
        String user = SecurityUtils.getCurrentUserLogin();
        log.info("Request to download postman environment file for Project " + id + " (" + user + ")");

        Operino project = projectService.findOneNoAuth(id);

        if (null == project) {
            return new HttpEntity(HttpStatus.BAD_REQUEST);
        }

        try {
            Map<String, String> config = projectService.getConfigForOperino(project);
            ParameterCollector collector = new ParameterCollector(ehrClient, config);
            byte[] document = collector.getPostmanConfig().toString().getBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String fileName = config.get(OperinoService.DOMAIN) + "_postman_env.json";
            headers.set("Content-Disposition", "attachment; filename=" + fileName);
            headers.setContentLength(document.length);

            return new HttpEntity<>(document, headers);
        } catch (JSONException e) {
            return new HttpEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
