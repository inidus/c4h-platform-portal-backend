package cloud.c4h.platform.repository.search;

import cloud.c4h.platform.domain.OperinoComponent;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data Elasticsearch repository for the OperinoComponent entity.
 */
public interface OperinoComponentSearchRepository extends ElasticsearchRepository<OperinoComponent, Long> {
}
