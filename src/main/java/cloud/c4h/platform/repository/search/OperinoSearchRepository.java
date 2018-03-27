package cloud.c4h.platform.repository.search;

import cloud.c4h.platform.domain.Operino;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data Elasticsearch repository for the Operino entity.
 */
public interface OperinoSearchRepository extends ElasticsearchRepository<Operino, Long> {
}
