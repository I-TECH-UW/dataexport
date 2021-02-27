package org.itech.fhir.dataexport.core.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;

public interface FhirClientFetcher {

	IGenericClient getFhirClient(String fhirStorePath);
}
