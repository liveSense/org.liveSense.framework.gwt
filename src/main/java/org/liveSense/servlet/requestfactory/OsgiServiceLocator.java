package org.liveSense.servlet.requestfactory;

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.google.web.bindery.requestfactory.shared.ServiceLocator;

public class OsgiServiceLocator implements ServiceLocator {

	// Caching services
	static Map<Class, Object> serviceCache = new HashMap<Class, Object>();

	public Object getInstance(Class<?> clazz) {
		if (clazz != null) {
			// If the service is not presented in cache
			Object service = serviceCache.get(clazz);
			if (service == null) {
				Bundle bundle = FrameworkUtil.getBundle(clazz);
				if (bundle != null) {
					serviceCache.put(clazz, bundle.getBundleContext().getService(bundle.getBundleContext().getServiceReference(clazz.getName())));
				}
			}
			return serviceCache.get(clazz);
		}
		return null;
	}
	
	public static void removeFromCache(Class<?> clazz) {
		serviceCache.remove(clazz);
	}
	
	public static void addToCache(Class<?> clazz, Object obj) {
		serviceCache.put(clazz, obj);
	}

}
