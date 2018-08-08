package cloud.c4h.platform.service.impl;

import cloud.c4h.platform.domain.Operino;
import cloud.c4h.platform.service.OperinoConfiguration;
import cloud.c4h.platform.service.util.ThinkEhrRestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static cloud.c4h.platform.service.OperinoService.*;

@Service
@Transactional
public class OperinoConfigurationImpl  implements OperinoConfiguration {

    private final ThinkEhrRestClient thinkEhrRestClient;

    public OperinoConfigurationImpl(ThinkEhrRestClient thinkEhrRestClient) {
        this.thinkEhrRestClient = thinkEhrRestClient;
    }

    private static final String DOMAIN_PASSWORD = "$2a$10$619ki";

    /**
     * Gets config associated with an operino
     *
     * @param operino the operino to get config for
     * @return the congig as a map
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getConfigForOperino(Operino operino) {
        String name = operino.getUser().getFirstName() + operino.getUser().getLastName();
        if (name.length() < 1) {
            name = operino.getDomain();
        }

        String user = operino.getDomain();
        // String pass = operino.getUser().getPassword().substring(0, 12);
        String pass = DOMAIN_PASSWORD;

        // create Map of data to be posted for domain creation
        Map<String, String> data = new HashMap<>();
        data.put(EXPLORER, this.thinkEhrRestClient.getExplorerUrl());
        data.put(DOMAIN, operino.getDomain());
        data.put(DOMAIN_SYSTEM_ID, operino.getDomain());
        data.put(USER_DISPLAY_NAME_OR_DOMAIN, name);
        data.put(OPERINO_NAME, operino.getName());
        data.put(USERNAME, user);
        data.put(PASSWORD, pass);
        data.put(API_TOKEN,thinkEhrRestClient.createBasicAuthString(user,pass));
        data.put(CDR, this.thinkEhrRestClient.getBaseUrl());


        return data;
    }
}
