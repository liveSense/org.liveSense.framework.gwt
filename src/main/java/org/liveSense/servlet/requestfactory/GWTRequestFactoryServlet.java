/*
 *  Copyright 2010 Robert Csakany <robson@semmi.se>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.liveSense.servlet.requestfactory;
/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Jan 04, 2011
 */
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.web.bindery.requestfactory.server.RequestFactoryServlet;

import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.PackageAdmin;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.jcr.LoginException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.servlet.gwtrpc.CompositeClassLoader;
import org.liveSense.servlet.gwtrpc.exceptions.AccessDeniedException;
import org.liveSense.servlet.gwtrpc.exceptions.InternalException;
import org.liveSense.core.BundleProxyClassLoader;
import org.liveSense.core.Configurator;
import org.liveSense.core.wrapper.RequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extending google's request factory servlet
 * <p/>
 * This class is for version 2.3.0 of the GWT and it is highly recommended to compile
 * client apps with the corresponding 2.3.0 GWT compiler only!
 * <p/>
 * GWT request factory servlets that are used in sling are required to extend the <code>SlingRemoteServiceServlet</code>
 * instead of google's own <code>RequestFactoryServlet</code>.
 * <p/>
 * It is important that any clientBundle using the Sling GWT Servlet Library imports the required packages from this clientBundle,
 * for otherwise the calls will fail due to well hidden <code>ClassNotFoundException</code>s. The client app will in
 * such a case only report "This application is outdated, please hit refresh...". As such, import the following
 * packages:
 * <p/>
 * <code>
 * com.google.gwt.user.server.*;
*  com.google.web.bindery.*;
 * </code>
 */

@Component(componentAbstract=true)
public abstract class GWTRequestFactoryServlet extends RequestFactoryServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2565545902953291699L;
	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(GWTRequestFactoryServlet.class);

	private final Logger payloadLogger = LoggerFactory.getLogger("GWTREQUESTFACTORY");
	
	@Reference
	private Configurator config;
	
	@Reference
	SlingRepository repository;

	@Reference
	PackageAdmin packageAdmin;
	
	@Reference
	AuthenticationSupport auth;
	
	@Reference
	ResourceResolverFactory resourceResolverFactory;
	
	public ClassLoader getClassLoaderByBundle(String name) throws ClassNotFoundException {
		return new BundleProxyClassLoader(getBundleByName(name));
	}

	
	private HashMap<String, ClassLoader> classLoaders = new HashMap<String, ClassLoader>();


    /**
     *
     * Allows the extending OSGi service to set its classloader.
     *
     * @param classLoader The classloader to provide to the SlingRemoteServiceServlet.
     */
    protected void setClassLoader(ClassLoader classLoader) {
        //this.classLoader = classLoader;
    	classLoaders.put(classLoader.toString(), classLoader);
    }


	public abstract void callInit() throws Throwable;

	public abstract void callFinal() throws Throwable;
	
    /**
     * Exception handler. Doublle exception handling
     * @param phase
     * @param payload
     * @param e
     * @return
     */
    private String processException(String phase, String payload, Throwable e) {
        String ret = "EX";
        try {
        	//ret = RPC.encodeResponseForFailure(null, e);
            payloadLogger.error(">>> ("+phase+") User: "+getUser()+" Payload: "+payload+" Return: "+ret, e);
        } catch (Exception ex) {
        	try {
				//ret = RPC.encodeResponseForFailure(null, new SerializationException("Serialization error", ex));
			} catch (Exception e2) {
			}
            payloadLogger.error(">>> ("+phase+") User: "+getUser()+" Payload: "+payload+" Return: "+ret, ex);
        }
        payloadLogger.info("<<< ("+phase+") User: "+getUser()+" Return: "+ret);
        return ret;
    }

	
    private static final String JSON_CHARSET = "UTF-8";
    private static final String JSON_CONTENT_TYPE = "application/json";

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException,
			ServletException {

		String payload = RPCServletUtils.readContent(request, JSON_CONTENT_TYPE, JSON_CHARSET);
		
        ClassLoader oldClassLoader = null;
        boolean osgiContext =false;

        // Custom classloader - OSGi context
        if (!classLoaders.isEmpty()) {
        	osgiContext = true;
        }
        	

        if (osgiContext) {
        	oldClassLoader = Thread.currentThread().getContextClassLoader();

            // Generating composite classloader from map
            CompositeClassLoader cClassLoader = new CompositeClassLoader();
            for (String key : classLoaders.keySet()) {
                cClassLoader.add(classLoaders.get(key));            	
            }
            
            // Set contextClassLoader
            Thread.currentThread().setContextClassLoader(cClassLoader);
        }    
        try {
            // Authenticating - OSGi context
            if (osgiContext) {
            	auth.handleSecurity(getThreadLocalRequest(), getThreadLocalResponse());
            }
            
            // CallInit
            try {
            	callInit();

                // ProcessCall
                doPost(request, response);

            	payloadLogger.info (">>> (callInit) User: "+getUser()+" Payload: "+payload);
            } catch (Throwable e) {
            	response.getWriter().append(processException("callInit", payload, e));
   			} finally {
				// callFinal
				try {
					callFinal();
				} catch (Throwable e) {
	            	payloadLogger.error (">>> (callFinal) User: "+getUser()+" Payload: "+payload);
				} finally {
				}
			}
        } finally {
            if (osgiContext) {
            	Thread.currentThread().setContextClassLoader(oldClassLoader);
            }
        }

	}
    	
	protected RequestWrapper getRequest() {
		return new RequestWrapper(this.getThreadLocalRequest(), null);
	}

	protected String getUser() {
		return (String)this.getThreadLocalRequest().getAttribute("org.osgi.service.http.authentication.remote.user");
	}
	
	public Bundle getBundleByName(String name) {
		 Bundle[] ret = packageAdmin.getBundles(name, null);
		 if (ret != null && ret.length > 0) {
			 return ret[0];
		 }
		 return null;
	}

	protected Locale getLocale() {
		if (getThreadLocalRequest().getAttribute("locale") == null) {
			RequestWrapper rw = new RequestWrapper(getThreadLocalRequest(), config.getDefaultLocale());
			getThreadLocalRequest().setAttribute("locale", rw.getLocale());
			return rw.getLocale();
		} else {
			return (Locale)getThreadLocalRequest().getLocale();
		}
	}
	
	protected void setLocale(Locale locale) {
		getThreadLocalRequest().setAttribute("locale", locale);		
	}
	
	public String formatMessage(String key, Object[] args) {
		String message = getResourceBundle().getString(key);
		return MessageFormat.format(message, args);
	}
	
	protected Session getUserSession(Repository repository) throws AccessDeniedException, InternalException {
		try {
			AuthenticationInfo info = (AuthenticationInfo) this.getThreadLocalRequest().getAttribute("org.apache.sling.commons.auth.spi.AuthenticationInfo");
            return repository.login(new SimpleCredentials(getUser(), info.getPassword()));
		} catch (LoginException ex) {
			throw new AccessDeniedException(formatMessage("accessDeniedForUser",new Object[]{getUser(), ex}));
		} catch (RepositoryException ex) {
			throw new InternalException(formatMessage("repositoryException", new Object[]{ex}));
		}
	}

	public ResourceBundle getResourceBundle() {
		return (ResourceBundle)getThreadLocalRequest().getAttribute("resourceBundle");
	}
	
	public void setResourceBundle(
		ResourceBundle resourceBundle) {
		getThreadLocalRequest().setAttribute("resourceBundle", resourceBundle);
	
	}

	public SlingRepository getRepository() {
		return repository;
	}

	public void setRepository(SlingRepository repository) {
		this.repository = repository;
	}		
	
	
}
