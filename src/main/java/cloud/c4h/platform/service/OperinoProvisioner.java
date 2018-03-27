package cloud.c4h.platform.service;

import cloud.c4h.platform.domain.Operino;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * Service that provisions operinos - This is tied into a RabbitMq receive event.
 */
public interface OperinoProvisioner {
    void receive(@Payload Operino operino);
}
