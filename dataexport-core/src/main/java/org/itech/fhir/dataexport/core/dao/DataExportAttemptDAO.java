package org.itech.fhir.dataexport.core.dao;

import java.util.List;

import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExportAttemptDAO extends CrudRepository<DataExportAttempt, Long> {

	@Query("SELECT d FROM DataExportAttempt d ORDER BY d.startTime DESC")
	List<DataExportAttempt> findLatestDataExportAttempts(Pageable limit);

	@Query("SELECT d FROM DataExportAttempt d WHERE d.dataExportTask.id = :dataExportTaskId  ORDER BY d.startTime DESC")
	List<DataExportAttempt> findLatestDataExportAttemptsByDataExportTask(Pageable limit,
			@Param("dataExportTaskId") Long dataExportTaskId);

	@Query("SELECT d FROM DataExportAttempt d WHERE d.dataExportTask.id = :dataExportTaskId AND d.dataExportStatus = :dataExportStatus ORDER BY d.startTime DESC")
	List<DataExportAttempt> findLatestDataExportAttemptsByDataExportTaskAndStatus(Pageable limit,
			@Param("dataExportTaskId") Long dataExportTaskId,
			@Param("dataExportStatus") String statusName);
}
