package org.liveSense.servlet.requestfactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
	
	@Override
	  public Object invoke(Method domainMethod, Object... args) {
	    Throwable ex;
	    try {
	      domainMethod.setAccessible(true);
	      if (Modifier.isStatic(domainMethod.getModifiers())) {
	        return domainMethod.invoke(null, args);
	      } else {
	        Object[] realArgs = new Object[args.length - 1];
	        System.arraycopy(args, 1, realArgs, 0, realArgs.length);
	        return domainMethod.invoke(args[0], realArgs);
	      }
	    } catch (IllegalArgumentException e) {
	      ex = e;
	    } catch (IllegalAccessException e) {
	      ex = e;
	    } catch (InvocationTargetException e) {
	      //return report(e);
	    	ex = e.getTargetException();
	    }
	    return die(ex, "Could not invoke method %s", domainMethod.getName());
	  }

	
}
