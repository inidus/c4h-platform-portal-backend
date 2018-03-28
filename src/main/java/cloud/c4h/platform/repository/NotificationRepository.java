package cloud.c4h.platform.repository;

import cloud.c4h.platform.domain.Notification;
import cloud.c4h.platform.domain.Operino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link cloud.c4h.platform.domain.Notification}s.
 */
@SuppressWarnings("unused")
public interface NotificationRepository extends JpaRepository<Notification,Long> {

    @Query("select notification from Notification notification where notification.operino.user.login = :username")
    Page<Notification> findByUserIsCurrentUser(@Param("username") String username, Pageable pageable);

    @Query("select notification from Notification notification where notification.operino.user.login = :username and notification.id = :id")
    Notification findOneByUserAndId(@Param("username") String username, @Param("id") Long notificationId);
}
