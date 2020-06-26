package org.itech.fhir.dataexport.api.service.impl;

import java.time.Instant;

import org.itech.fhir.dataexport.api.service.DataExportStatusService;
import org.itech.fhir.dataexport.api.service.event.DataExportStatusEvent;
import org.itech.fhir.dataexport.core.dao.DataExportAttemptDAO;
import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataExportStatusServiceImpl implements DataExportStatusService {

	private DataExportAttemptDAO dataExportAttemptRepository;
	private ApplicationEventPublisher applicationEventPublisher;

	public DataExportStatusServiceImpl(DataExportAttemptDAO dataExportAttemptRepository,
			ApplicationEventPublisher applicationEventPublisher) {
		this.dataExportAttemptRepository = dataExportAttemptRepository;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Transactional
	@EventListener(DataExportStatusEvent.class)
	@Override
	public void onApplicationEvent(DataExportStatusEvent event) {
		switch (event.getDataExportStatus()) {
		case GENERATED:
			// never published
			break;
		case REQUESTING:
			break;
		case COLLECTED:
			break;
		case EXPORTING:
			break;
		case SUCCEEDED:
		case FAILED:
		case INCOMPLETE:
		case NOT_RAN:
			event.getDataExportAttempt().setEndTime(Instant.now());
		}
		event.getDataExportAttempt().setDataExportStatus(event.getDataExportStatus());
		dataExportAttemptRepository.save(event.getDataExportAttempt());
	}

	@Override
	public void changeDataRequestAttemptStatus(DataExportAttempt dataExportAttempt, DataExportStatus dataExportStatus) {
		publishDataRequestEvent(dataExportAttempt, dataExportStatus);
	}

	private void publishDataRequestEvent(DataExportAttempt dataExportAttempt, DataExportStatus dataExportStatus) {
		log.debug("publishing dataExportEvent for dataExportAttempt " + dataExportAttempt.getId() + " with status "
				+ dataExportStatus);
		applicationEventPublisher.publishEvent(new DataExportStatusEvent(this, dataExportAttempt, dataExportStatus));
	}

}
