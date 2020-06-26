package org.itech.fhir.dataexport.core.dao;

import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExportTaskDAO extends CrudRepository<DataExportTask, Long> {

}
