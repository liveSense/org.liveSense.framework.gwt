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
package org.liveSense.servlet.gwtrpc;
/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 12, 2010
 */
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;

import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.PackageAdmin;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.jcr.LoginException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.auth.core.impl.SlingAuthenticator;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.servlet.gwtrpc.exceptions.AccessDeniedException;
import org.liveSense.servlet.gwtrpc.exceptions.InternalException;
import org.liveSense.core.BundleProxyClassLoader;
import org.liveSense.core.Configurator;
import org.liveSense.core.wrapper.I18nResourceWrapper;
import org.liveSense.core.wrapper.RequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extending google's remote service servlet to enable resolving of resources through
 * a clientBundle (for policy file loading).
 * <p/>
 * This class is for version 2.0.3 of the GWT gwt-servlet.jar edition and it is highly recommended to compile
 * client apps with the corresponding 2.0.3 GWT compiler only!
 * <p/>
 * GWT service servlets that are used in sling are required to extend the <code>SlingRemoteServiceServlet</code>
 * instead of google's own <code>RemoteServiceServlet</code>.
 * <p/>
 * It is important that any clientBundle using the Sling GWT Servlet Library imports the required packages from this clientBundle,
 * for otherwise RPC calls will fail due to well hidden <code>ClassNotFoundException</code>s. The client app will in
 * such a case only report "This application is outdated, please hit refresh...". As such, import the following
 * packages:
 * <p/>
 * <code>
 * org.apache.sling.extensions.gwt.user.server.rpc,
 * com.google.gwt.core.client,
 * com.google.gwt.http.client,
 * com.google.gwt.i18n.client,
 * com.google.gwt.i18n.client.constants,
 * com.google.gwt.i18n.client.impl,
 * com.google.gwt.junit.client,
 * com.google.gwt.junit.client.impl,
 * com.google.gwt.user.client,
 * com.google.gwt.user.client.impl,
 * com.google.gwt.user.client.rpc,
 * com.google.gwt.user.client.rpc.core.java.lang,
 * com.google.gwt.user.client.rpc.core.java.util,
 * com.google.gwt.user.client.rpc.impl,
 * com.google.gwt.user.client.ui,
 * com.google.gwt.user.client.ui.impl,
 * com.google.gwt.user.server.rpc,
 * com.google.gwt.user.server.rpc.impl,
 * com.google.gwt.xml.client,
 * com.google.gwt.xml.client.impl
 * </code>
 */

@Component(componentAbstract=true)
public abstract class GWTServiceServlet extends RemoteServiceServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2565545902953291699L;
	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(GWTServiceServlet.class);
    /**
     * The <code>org.osgi.framework.Bundle</code> to load resources from.
     */
    private Bundle clientBundle;

	private String rootPath = "";
	
	@Reference
	private Configurator config;
	
	@Reference
	SlingRepository repository;

	@Reference
	PackageAdmin packageAdmin;
	
	@Reference
	AuthenticationSupport auth;
	
	public ClassLoader getClassLoaderByBundle(String name) throws ClassNotFoundException {
		return new BundleProxyClassLoader(getBundleByName(name));
	}

	
	private HashMap<String, ClassLoader> classLoaders = new HashMap<String, ClassLoader>();


    /**
     * Allows the extending OSGi service to set its classloader.
     *
     * @param classLoader The classloader to provide to the SlingRemoteServiceServlet.
     */
    protected void setClassLoader(ClassLoader classLoader) {
        //this.classLoader = classLoader;
    	classLoaders.put(classLoader.toString(), classLoader);
    }

    /**
     * The <code>ClassLoader</code> to use when GWT reflects on RPC classes.
     */
    //private ClassLoader classLoader = null;

    /**
     * Process a call originating from the given request. Uses the
     * {@link com.google.gwt.user.server.rpc.RPC#invokeAndEncodeResponse(Object, java.lang.reflect.Method, Object[])}
     * method to do the actual work.
     * <p>
     * Subclasses may optionally override this method to handle the payload in any
     * way they desire (by routing the request to a framework component, for
     * instance). The {@link javax.servlet.http.HttpServletRequest} and {@link javax.servlet.http.HttpServletResponse}
     * can be accessed via the {@link #getThreadLocalRequest()} and
     * {@link #getThreadLocalResponse()} methods.
     * </p>
     * This is public so that it can be unit tested easily without HTTP.
     * <p/>
     * In order to properly operate within Sling/OSGi, the classloader used by GWT has to be rerouted from
     * <code>Thread.currentThread().getContextClassLoader()</code> to the classloader provided by the Bundle.
     *
     * @param payload the UTF-8 request payload
     * @return a string which encodes either the method's return, a checked
     *         exception thrown by the method, or an
     *         {@link com.google.gwt.user.client.rpc.IncompatibleRemoteServiceException}
     * @throws com.google.gwt.user.client.rpc.SerializationException
     *                          if we cannot serialize the response
     * @throws com.google.gwt.user.server.rpc.UnexpectedException
     *                          if the invocation throws a checked exception
     *                          that is not declared in the service method's signature
     * @throws RuntimeException if the service method throws an unchecked
     *                          exception (the exception will be the one thrown by the service)
     */
    @Override
    public String processCall(String payload) throws SerializationException {
        String result;
        if (!classLoaders.isEmpty()) {
            final ClassLoader old = Thread.currentThread().getContextClassLoader();
            // Generating composite classloader from map
            
            CompositeClassLoader cClassLoader = new CompositeClassLoader();
            for (String key : classLoaders.keySet()) {
                cClassLoader.add(classLoaders.get(key));            	
            }
            Thread.currentThread().setContextClassLoader(cClassLoader);
            auth.handleSecurity(getThreadLocalRequest(), getThreadLocalResponse());
			callInit();
            result = super.processCall(payload);
            Thread.currentThread().setContextClassLoader(old);
        } else {
        	callInit();
        	try {
        		result = super.processCall(payload);
        	} finally {
        		callFinal();
        	}
        }
        return result;
    }

	public abstract void callInit();

	public abstract void callFinal();

	
    /**
     * Gets the {@link com.google.gwt.user.server.rpc.SerializationPolicy} for given module base URL and strong
     * name if there is one.
     * <p/>
     * Override this method to provide a {@link com.google.gwt.user.server.rpc.SerializationPolicy} using an
     * alternative approach.
     * <p/>
     * This method has been overriden, so that the serialization policy can be properly loaded as a Bundle entry,
     * as Sling does not support <code>ServletContext.getResourceAsStream()</code>.
     *
     * @param request       the HTTP request being serviced
     * @param moduleBaseURL as specified in the incoming payload
     * @param strongName    a strong name that uniquely identifies a serialization
     *                      policy file
     * @return a {@link com.google.gwt.user.server.rpc.SerializationPolicy} for the given module base URL and
     *         strong name, or <code>null</code> if there is none
     */
    @Override
    protected SerializationPolicy doGetSerializationPolicy(HttpServletRequest request, String moduleBaseURL, String strongName) {
    	
        // The request can tell you the path of the web app relative to the
        // container root.
        String contextPath = request.getContextPath();

        String modulePath = null;
        if (moduleBaseURL != null) {
            try {
                modulePath = new URL(moduleBaseURL).getPath();
            } catch (MalformedURLException ex) {
                // log the information, we will default
                getServletContext().log("Malformed moduleBaseURL: " + moduleBaseURL, ex);
            }
        }

        SerializationPolicy serializationPolicy = null;

        /*
        * Check that the module path must be in the same web app as the servlet
        * itself. If you need to implement a scheme different than this, override
        * this method.
        */
        if (modulePath == null || !modulePath.startsWith(contextPath)) {
            String message = "ERROR: The module path requested, "
                    + modulePath
                    + ", is not in the same web application as this servlet, "
                    + contextPath
                    + ".  Your module may not be properly configured or your client and server code maybe out of date.";
            getServletContext().log(message);
        } else {
            // Strip off the context path from the module base URL. It should be a
            // strict prefix.
            String contextRelativePath = rootPath + modulePath.substring(contextPath.length());

            String serializationPolicyFilePath = SerializationPolicyLoader.getSerializationPolicyFileName(contextRelativePath
                    + strongName);

            // Open the RPC resource file read its contents.
            InputStream is = null;
            // if the clientBundle was set by the extending class, load the resource from it instead of the servlet context
            if (clientBundle != null) {
                try {
                    is = clientBundle.getResource(serializationPolicyFilePath).openStream();
                } catch (IOException e) {
                    //ignore
                } catch (NullPointerException e) {
					
				}
            } else {
                is = getServletContext().getResourceAsStream(
                        serializationPolicyFilePath);
            }
            try {
                if (is != null) {
                    try {
                        //serializationPolicy = SerializationPolicyLoader.loadFromStream(is);
                    	
                    	ArrayList<ClassNotFoundException> errorList = new ArrayList<ClassNotFoundException>();
                    	serializationPolicy = SerializationPolicyLoader.loadFromStream(is, errorList);
                    	
                    	if (errorList != null && errorList.size()>0) {
                    		for (ClassNotFoundException e : errorList) {
	                            getServletContext().log(
	                                    "ERROR: Could not find class '" + e.getMessage()
	                                            + "' listed in the serialization policy file '"
	                                            + serializationPolicyFilePath + "'"
	                                            + "; your server's classpath may be misconfigured", e);
                    		}
                    	}
                    } catch (ParseException e) {
                        getServletContext().log(
                                "ERROR: Failed to parse the policy file '"
                                        + serializationPolicyFilePath + "'", e);
                    } catch (IOException e) {
                        getServletContext().log(
                                "ERROR: Could not read the policy file '"
                                        + serializationPolicyFilePath + "'", e);
                    }
                } else {
                    String message = "ERROR: The serialization policy file '"
                            + serializationPolicyFilePath
                            + "' was not found; did you forget to include it in this deployment?";
                    getServletContext().log(message);
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore this error
                    }
                }
            }
        }

        return serializationPolicy;
    }

    /**
     * Allows the extending OSGi service to set the clientBundle it is part of. The clientBundle is used to provide access
     * to the policy file otherwise loaded by <code>getServletContext().getResourceAsStream()</code> which is not
     * supported in Sling.
     *
     * @param clientBundle The clientBundle to load the resource (policy file) from.
     */
    protected void setClientBundle(Bundle bundle) {
        this.clientBundle = bundle;
    }
    	

	protected void setRootPath(String rootPath) {
		this.rootPath = rootPath;
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

	public String getExceptionMessage(Throwable th) {
		return formatMessage("exception",new Object[]{th.getClass().getName(), th.getStackTrace()[0].getFileName(), th.getStackTrace()[0].getMethodName(), th.getStackTrace()[0].getLineNumber()});
	}

	protected Object throwRPCExceptionLocalized(String key) throws Exception  {
		return throwRPCExceptionLocalized(key, null, null);
	}

	protected Object throwRPCExceptionLocalized(String key, Object[] args) throws Exception  {
		return throwRPCExceptionLocalized(key, null, args);
	}

	protected Object throwRPCExceptionLocalized(String key, Throwable th) throws Exception  {
		return throwRPCExceptionLocalized(key, th, null);
	}

	protected Object throwRPCExceptionLocalized(String key, Throwable th, Object[] args) throws Exception  {
		String message = getResourceBundle().getString(key);
		return throwRPCException(message, th, args);
	}
	
	protected Object throwRPCException(String message) throws Exception  {
		return throwRPCExceptionLocalized(message, null, null);
	}

	protected Object throwRPCException(String message, Object[] args) throws Exception  {
		return throwRPCException(message, null, args);
	}

	protected Object throwRPCException(String message, Throwable th) throws Exception  {
		return throwRPCException(message, th, null);
	}

	protected Object throwRPCException(String message, Throwable th, Object[] args) throws Exception  {
		String result = message;

		if (args != null) {

			try {
				StringBuffer sb = new StringBuffer();
				MessageFormat.format(message, args, sb, null);
				result = sb.toString();
			} catch (IllegalArgumentException e1) {
				log.error("Error in format message", e1);
			}
		}
		if (th != null) log.error(result, th);
		else log.error(result);

		if (th != null) {
			if (th instanceof InternalException) {
				throw (InternalException)th;
			} else if (th instanceof AccessDeniedException) {
				throw (AccessDeniedException)th;
			} else throw new InternalException(result, th);
		}
		throw new InternalException(result);
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
