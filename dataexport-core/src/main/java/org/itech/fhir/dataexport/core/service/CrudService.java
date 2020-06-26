package org.itech.fhir.dataexport.core.service;

import org.springframework.data.repository.CrudRepository;

public interface CrudService<T, ID> {

	CrudRepository<T, ID> getDAO();

}
