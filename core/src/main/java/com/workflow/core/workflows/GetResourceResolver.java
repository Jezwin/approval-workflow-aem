package com.workflow.core.workflows;

import java.util.Collections;

import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;

@Component(service = WorkflowProcess.class,  
immediate = true, enabled = true,
property = {"process.label= Get Resourceresolver"})
public class GetResourceResolver implements WorkflowProcess {
	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Override
	public void execute(WorkItem item, WorkflowSession wfsession, MetaDataMap args) throws WorkflowException {
		ResourceResolver resourceResolver = null;


		try
		{
			resourceResolver = getResourceResolver(wfsession.getSession());
			item.getMetaDataMap().put("resourceResolver", resourceResolver);
		}
		catch (Exception e)
		{
			e.printStackTrace(); 
		}
	}
	private ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
				session));
	}
}