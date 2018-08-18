package cloud.c4h.platform.service;

import cloud.c4h.platform.domain.Notification;
import cloud.c4h.platform.domain.Operino;
import cloud.c4h.platform.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Service Interface for managing Operino.
 */
public interface OperinoService {
    String DOMAIN = "domainName";
    String OPERINO_NAME = "operinoName";
    String USERNAME = "username";
    String PASSWORD = "password";
    String API_TOKEN = "token";
    // holds the same value as DOMAIN
    String DOMAIN_SYSTEM_ID = "domainSystemId";
    String USER_DISPLAY_NAME_OR_DOMAIN = "name";
    String CDR = "cdr";
    String EXPLORER = "explorer";
    String OVERVIEW_DOCS = "docs";
    String EHR_API_DOCS = "ehrApiDocs";
    String ADMIN_API_DOCS = "adminApiDocs";


    /**
     * Static utility method to create an Operino with the given parameters
     */
    static Operino createOperino(String name, User user, boolean active, boolean provision) {
        Operino operino = new Operino();
        operino.setName(name);
        operino.setUser(user);
        operino.setActive(active);
        operino.setProvision(provision);
        return operino;
    }

    /**
     * Save a operino.
     *
     * @param operino the entity to save
     * @return the persisted entity
     */
    Operino save(Operino operino);

    /**
     * Get all the operinos.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    Page<Operino> findAll(Pageable pageable);

    /**
     * Get the "id" operino if the current user is the owner or has ADMIN role
     *
     * @param id the id of the entity
     * @return the entity
     */
    Operino verifyOwnershipAndGet(Long id);

    /**
     * Get the "id" operino.
     *
     * @param id the id of the entity
     * @return the entity
     */
    Operino findOne(Long id);

    /**
     * Get one operino by id. No authorisation check is done!
     *
     * @param id the id of the entity
     * @return the entity
     */
    public Operino findOneNoAuth(Long id);

    /**
     * Delete the "id" operino.
     *
     * @param id the id of the entity
     */
    void delete(Long id);

    /**
     * Search for the operino corresponding to the query.
     * <p>
     *
     * @param query    the query of the search
     * @param pageable the pagination information
     * @return the list of entities
     */
    Page<Operino> search(String query, Pageable pageable);

    //Map<String, String> getConfigForOperino(Operino operino);

    Notification sendNotification(Notification notification);

    Page<Notification> getNotifications(Pageable pageable);

    /**
     * Add components according to configuration
     */
    Operino addDefaultComponents(Operino operino);
}
