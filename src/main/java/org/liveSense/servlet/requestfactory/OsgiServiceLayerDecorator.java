package org.liveSense.servlet.requestfactory;

import com.google.web.bindery.requestfactory.server.ServiceLayerDecorator;

public class OsgiServiceLayerDecorator extends ServiceLayerDecorator {

	ClassLoader classLoader = null;
	public OsgiServiceLayerDecorator(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	
	@Override
	public ClassLoader getDomainClassLoader() {
		return classLoader;
	}
	
	public ClassLoader getClassLoader() {
		return classLoader;
	}
}
