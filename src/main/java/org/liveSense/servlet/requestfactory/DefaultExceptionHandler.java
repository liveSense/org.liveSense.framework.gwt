package org.liveSense.servlet.requestfactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.web.bindery.requestfactory.server.ExceptionHandler;
import com.google.web.bindery.requestfactory.shared.ServerFailure;

public class DefaultExceptionHandler implements ExceptionHandler {
	Logger log = LoggerFactory.getLogger(DefaultExceptionHandler.class);
	/**
	 * 
	 */
	private GWTRequestFactoryServlet requestFactoryServlet;
	
	public ServerFailure createServerFailure(Throwable throwable) {
		log.error("Faliure: ", throwable);
		ServerFailure failure = null;
		try {
			failure = this.requestFactoryServlet.faliure(throwable);
		} catch (Throwable e) {
		}
		if (failure == null) failure = new ServerFailure( throwable.getMessage(), throwable.getClass().getName(), null, true );
		return failure;
	}

	/**
	 * @return the requestFactoryServlet
	 */
	public GWTRequestFactoryServlet getRequestFactoryServlet() {
		return requestFactoryServlet;
	}

	/**
	 * @param requestFactoryServlet the requestFactoryServlet to set
	 */
	public void setRequestFactoryServlet(GWTRequestFactoryServlet requestFactoryServlet) {
		this.requestFactoryServlet = requestFactoryServlet;
	}
	
}