package org.itech.fhir.dataexport.api.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.ResourceType;
import org.itech.fhir.dataexport.api.service.DataExportService;
import org.itech.fhir.dataexport.api.service.DataExportStatusService;
import org.itech.fhir.dataexport.core.dao.DataExportAttemptDAO;
import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.itech.fhir.dataexport.core.service.DataExportTaskService;
import org.itech.fhir.dataexport.core.service.FhirClientFetcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.param.DateRangeParam;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class DataExportServiceImpl implements DataExportService {

	@Value("${org.openelisglobal.fhirstore.uri}")
	private String localFhirStore;

	@Value("${org.openelisglobal.fhir.subscriber}")
	private String defaultRemoteServer;

	@Value("${org.openelisglobal.fhir.subscriber.resources}")
	private String[] defaultResources;

	private DataExportTaskService dataExportTaskService;
	private DataExportAttemptDAO dataExportAttemptDAO;
	private DataExportStatusService dataExportStatusService;
	private FhirContext fhirContext;
	private FhirClientFetcher clientFetcher;

	public DataExportServiceImpl(DataExportTaskService dataExportTaskService, DataExportAttemptDAO dataExportAttemptDAO,
			DataExportStatusService dataExportStatusService, FhirContext fhirContext, FhirClientFetcher clientFetcher) {
		this.dataExportTaskService = dataExportTaskService;
		this.dataExportAttemptDAO = dataExportAttemptDAO;
		this.dataExportStatusService = dataExportStatusService;
		this.fhirContext = fhirContext;
		this.clientFetcher = clientFetcher;
	}

	@Override
	@Async
	public Future<DataExportStatus> exportNewDataFromLocalToRemote(DataExportTask dataExportTask) {
		DataExportAttempt dataExportAttempt = dataExportAttemptDAO.save(new DataExportAttempt(dataExportTask));
		return runDataExportAttempt(dataExportAttempt);
	}

	private Future<DataExportStatus> runDataExportAttempt(DataExportAttempt dataExportAttempt) {
		List<Bundle> localBundles = new ArrayList<>();
		DataExportStatus status = getBundlesFromLocalServer(dataExportAttempt, localBundles);
		if (status.equals(DataExportStatus.COLLECTED)) {
			status = sendBundlesToRemote(dataExportAttempt, localBundles);
		}

		return new AsyncResult<>(status);
	}

	private DataExportStatus getBundlesFromLocalServer(DataExportAttempt dataExportAttempt, List<Bundle> localBundles) {

		DataExportTask dataExportTask = dataExportAttempt.getDataExportTask();
		Instant lastSuccess = dataExportTaskService.getLatestSuccessInstantForDataExportTask(dataExportTask);
		DateRangeParam dateRange = new DateRangeParam().setLowerBoundInclusive(Date.from(lastSuccess))
				.setUpperBoundInclusive(Date.from(dataExportAttempt.getStartTime()));
		try {
			dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt,
					DataExportStatus.REQUESTING);

			IGenericClient sourceFhirClient = clientFetcher.getFhirClient(localFhirStore);

			for (String resource : defaultResources) {
				Bundle localSearchBundle = sourceFhirClient//
						.search()//
						.forResource(resource)//
						.lastUpdated(dateRange)//
						.returnBundle(Bundle.class).execute();
				localBundles.add(localSearchBundle);
				log.trace("received json " + fhirContext.newJsonParser().encodeResourceToString(localSearchBundle));
				while (localSearchBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
					localSearchBundle = sourceFhirClient.loadPage().next(localSearchBundle).execute();
					log.trace("received json " + fhirContext.newJsonParser().encodeResourceToString(localSearchBundle));
					localBundles.add(localSearchBundle);
				}
			}
		} catch (RuntimeException e) {
			log.error("error occured while retrieving resources from local fhir store", e);
			dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.FAILED);
			return DataExportStatus.FAILED;
		}
		dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.COLLECTED);
		return DataExportStatus.COLLECTED;
	}

	private DataExportStatus sendBundlesToRemote(DataExportAttempt dataExportAttempt, List<Bundle> localSearchBundles) {
		boolean anyTransactionSucceeded = false;
		try {
			dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt,
					DataExportStatus.EXPORTING);

			IGenericClient remoteFhirClient = clientFetcher.getFhirClient(defaultRemoteServer);
			AdditionalRequestHeadersInterceptor interceptor = new AdditionalRequestHeadersInterceptor();
			Map<String, String> headers = dataExportAttempt.getDataExportTask().getHeaders();

			for (Entry<String, String> header : headers.entrySet()) {
				interceptor.addHeaderValue(header.getKey(), header.getValue());
			}
			remoteFhirClient.registerInterceptor(interceptor);

			for (Bundle localSearchBundle : localSearchBundles) {
				Bundle transactionBundle = createTransactionBundleFromSearchResponseBundle(localSearchBundle);
				log.trace("sending bundle to remote: "
						+ fhirContext.newJsonParser().encodeResourceToString(transactionBundle));
				Bundle transactionResponseBundle = remoteFhirClient//
						.transaction()//
						.withBundle(transactionBundle).execute();
				log.trace("received transaction response bundle from remote: "
						+ fhirContext.newJsonParser().encodeResourceToString(transactionResponseBundle));
				anyTransactionSucceeded = true;
			}
			dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt,
					DataExportStatus.SUCCEEDED);
			return DataExportStatus.SUCCEEDED;
		} catch (RuntimeException e) {
			log.error("error occured while sending resources to remote fhir store", e);
			DataExportStatus status = DataExportStatus.FAILED;
			if (anyTransactionSucceeded) {
				status = DataExportStatus.INCOMPLETE;
			}
			dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, status);
			return status;
		}
	}

	private Bundle createTransactionBundleFromSearchResponseBundle(Bundle searchBundle) {
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(BundleType.TRANSACTION);
		for (BundleEntryComponent searchComponent : searchBundle.getEntry()) {
			if (searchComponent.hasResource()) {
				BundleEntryComponent transactionComponent = createTransactionComponentFromSearchComponent(
						searchComponent);
				transactionBundle.addEntry(transactionComponent);
				transactionBundle.setTotal(transactionBundle.getTotal() + 1);
			}
		}
		return transactionBundle;
	}

	private BundleEntryComponent createTransactionComponentFromSearchComponent(BundleEntryComponent searchComponent) {
		ResourceType resourceType = searchComponent.getResource().getResourceType();
		String sourceResourceId = searchComponent.getResource().getIdElement().getIdPart();
		if (StringUtils.isNumeric(sourceResourceId)) {
			throw new IllegalArgumentException("id cannot be a number. Numbers are reserved for local entities only");
		}

		BundleEntryComponent transactionComponent = new BundleEntryComponent();
		transactionComponent.setResource(searchComponent.getResource());

		transactionComponent.getRequest().setMethod(HTTPVerb.PUT);
		transactionComponent.getRequest().setUrl(resourceType + "/" + sourceResourceId);

		return transactionComponent;
	}

}
