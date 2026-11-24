package com.veya.identity.infrastructure;

import com.veya.identity.domain.ConsentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Spring Data JPA repository of the versioned consent documents. */
public interface ConsentDocumentRepository extends JpaRepository<ConsentDocument, Short> {

    /** The current (latest published) version of every consent type. */
    @Query("""
            select d from ConsentDocument d
            where d.publishedOn = (select max(d2.publishedOn) from ConsentDocument d2 where d2.consentType = d.consentType)
              and d.id = (select max(d3.id) from ConsentDocument d3
                          where d3.consentType = d.consentType and d3.publishedOn = d.publishedOn)
            order by d.id""")
    List<ConsentDocument> findCurrent();
}
