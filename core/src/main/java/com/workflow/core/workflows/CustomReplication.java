package com.workflow.core.workflows;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


import javax.jcr.Session;
import javax.jcr.Node; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;

import javax.jcr.Repository; 
import javax.jcr.SimpleCredentials; 
import javax.jcr.Node; 
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import com.day.cq.dam.api.Asset; 
import java.util.Collections;

import org.apache.jackrabbit.commons.JcrUtils;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


import javax.jcr.Session;
import javax.jcr.Node; 
import org.osgi.framework.Constants;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;

//AssetManager
import com.day.cq.dam.api.AssetManager;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.commons.util.AssetReferenceSearch;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream ; 
import java.io.OutputStream ; 
import java.io.ByteArrayInputStream ; 
import java.io.FileOutputStream ; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.replication.Agent;
import com.day.cq.replication.AgentFilter;
//Replication APIs    
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationOptions;
import com.day.cq.replication.Replicator;


//Sling Imports
import org.apache.sling.api.resource.ResourceResolverFactory ; 
import org.apache.sling.api.resource.ResourceResolver; 
import org.apache.sling.api.resource.Resource; 
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.commons.ReferenceSearch;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.adobe.granite.workflow.model.WorkflowNode;
import com.adobe.granite.workflow.exec.WorkflowData;




//This custom workflow step will use the AEM Replication API to replicate content to QA environment
@Component(service=WorkflowProcess.class, property = {"process.label=Replication to QA"})

public class CustomReplication implements WorkflowProcess 
{


	protected final Logger log = LoggerFactory.getLogger(this.getClass());

	@Reference
	private ResourceResolverFactory resolverFactory;

	@Reference
	private Replicator replicator;

	private Session session;

	public void execute(WorkItem item, WorkflowSession wfsession,MetaDataMap args) throws WorkflowException {

		System.out.println("Inside replication");

		try
		{       

			WorkflowNode myNode = item.getNode(); 
			String myTitle = myNode.getTitle(); //returns the title of the workflow step     
			WorkflowData workflowData = item.getWorkflowData(); //gain access to the payload data
			String path = workflowData.getPayload().toString();//Get the path of the asset
			String replicationAgentsArray [] = null;
			
			if(args.containsKey("PROCESS_ARGS")) {	
				replicationAgentsArray = args.get("PROCESS_ARGS", "string").toString().split(",");
			}
			
			replicationContent(path, wfsession, replicationAgentsArray);       
		}

		catch (Exception e)
		{
			e.printStackTrace()  ; 
		}
	}


	private String replicationContent(String path, WorkflowSession wfsession, String[] replicationAgentsArray)
	{
		try
		{
			Session session = wfsession.adaptTo(Session.class);
			ResourceResolver resourceResolver = resolverFactory.getResourceResolver(Collections.singletonMap("user.jcr.session", (Object) session));

			ReplicationOptions options = new ReplicationOptions();
			options.setSuppressVersions(true);
			options.setSynchronous(true);
			options.setSuppressStatusUpdate(false); 
			
			for(String replicationAgent: replicationAgentsArray) {
				System.out.println(replicationAgent);
				replicateToQA(replicationAgent, options, path, resourceResolver, session);			
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			log.info("**** Error: " +e.getMessage()) ;  
		}
		return null;
	}


	private void replicateToQA(String replicationAgent, ReplicationOptions options, String path,
			ResourceResolver resourceResolver, Session session) throws ReplicationException {

		options.setFilter(new AgentFilter(){
			public boolean isIncluded(final Agent agent) {
				return replicationAgent.equals(agent.getId());
			}
		});
		
		//Replicate Page
		replicator.replicate(session,ReplicationActionType.ACTIVATE,path,options); 
		
		
		//Replicate Template
		Page page = resourceResolver.getResource(path).adaptTo(Page.class);
		replicator.replicate(session, ReplicationActionType.ACTIVATE,page.getTemplate().getPath(),options); 

		//Replicate Assets
		Set<String> pageAssetPaths = getPageAssetsPaths(resourceResolver, path);
		if(!pageAssetPaths.equals(null)) {
			System.out.println("Inside asset replication");
			for (String assetPath : pageAssetPaths) {
				replicator.replicate(session,ReplicationActionType.ACTIVATE,assetPath,options); 
			}
		}
	}


	private Set<String> getPageAssetsPaths(ResourceResolver resolver, String pagePath) {

		PageManager pageManager = resolver.adaptTo(PageManager.class);

		Page page = pageManager.getPage(pagePath);

		if (page == null) {
			return new LinkedHashSet<>();
		}

		Resource resource = page.getContentResource();
		AssetReferenceSearch assetReferenceSearch = new AssetReferenceSearch(resource.adaptTo(Node.class),
				DamConstants.MOUNTPOINT_ASSETS, resolver);
		Map<String, Asset> assetMap = assetReferenceSearch.search();

		return assetMap.keySet();
	}
}
