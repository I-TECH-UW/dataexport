package org.itech.fhir.dataexport.core.dao;

import java.util.Optional;

import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExportTaskDAO extends CrudRepository<DataExportTask, Long> {

	@Query("SELECT d FROM DataExportTask d WHERE d.endpoint = :endpoint")
	Optional<DataExportTask> findByEndpoint(@Param("endpoint") String endpoint);

}
