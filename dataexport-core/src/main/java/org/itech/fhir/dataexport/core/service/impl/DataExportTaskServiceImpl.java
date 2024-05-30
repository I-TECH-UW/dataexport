package org.itech.fhir.dataexport.core.service.impl;

import java.time.Instant;
import java.util.List;

import org.itech.fhir.dataexport.core.dao.DataExportAttemptDAO;
import org.itech.fhir.dataexport.core.dao.DataExportTaskDAO;
import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.itech.fhir.dataexport.core.service.DataExportTaskService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class DataExportTaskServiceImpl extends CrudServiceImpl<DataExportTask, Long> implements DataExportTaskService {

	private DataExportTaskDAO dataExportTaskDAO;
	private DataExportAttemptDAO dataExportAttemptDAO;

	public DataExportTaskServiceImpl(DataExportTaskDAO dataExportTaskDAO, DataExportAttemptDAO dataExportAttemptDAO) {
		super(dataExportTaskDAO);
		this.dataExportTaskDAO = dataExportTaskDAO;
		this.dataExportAttemptDAO = dataExportAttemptDAO;
	}

	@Override
	public DataExportTaskDAO getDAO() {
		return dataExportTaskDAO;
	}

	@Override
	public Instant getLatestSuccessInstantForDataExportTask(DataExportTask dataExportTask) {
		Instant lastSuccess = Instant.EPOCH;

		List<DataExportAttempt> lastExportAttempts = dataExportAttemptDAO
				.findLatestDataExportAttemptsByDataExportTaskAndStatus(PageRequest.of(0, 1), dataExportTask.getId(),
						DataExportStatus.SUCCEEDED.name());
		if (lastExportAttempts.size() == 1) {
			DataExportAttempt latestAttempt = lastExportAttempts.get(0);
			lastSuccess = latestAttempt.getStartTime();
		}
		log.debug("last data export success was at: " + lastSuccess);
		return lastSuccess;
	}

	@Override
	public Instant getLatestInstantForDataExportTask(DataExportTask dataExportTask) {
		Instant lastAttempt = Instant.EPOCH;

		List<DataExportAttempt> lastExportAttempts = dataExportAttemptDAO
				.findLatestDataExportAttemptsByDataExportTask(PageRequest.of(0, 1), dataExportTask.getId());
		if (lastExportAttempts.size() == 1) {
			DataExportAttempt latestAttempt = lastExportAttempts.get(0);
			lastAttempt = latestAttempt.getStartTime();
		}
		log.debug("last data export attempt was at: " + lastAttempt);
		return lastAttempt;
	}

}
