package com.workflow.core.workflows;



import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.exec.ParticipantStepChooser;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.workflow.WorkflowSession;


import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.mail.MessagingException;

import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service= ParticipantStepChooser.class,property = {"chooser.label=Custom dynamic participant chooser"})
public class DynamicParticipant implements ParticipantStepChooser {

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Reference
	private MessageGatewayService messageGatewayService;

	@Reference
	ConfigurationAdmin configurationAdmin;

	@Override
	public String getParticipant(WorkItem workItem, com.adobe.granite.workflow.WorkflowSession workflowSession, MetaDataMap metaDataMap)
			throws WorkflowException {

		ResourceResolver resourceResolver = null;

		String processArgumentsArray[] = null;
		String adminRequestTemplate = null;
		String adminRequestSubject = null;
		String adminGroup = null;
		String adminGroupKey = null;
		String path = workItem.getWorkflowData().getPayload().toString();
		String project = null;
		if(StringUtils.ordinalIndexOf(path, "/", 3)>0) {
			project = path.substring(StringUtils.ordinalIndexOf(path, "/", 2), StringUtils.ordinalIndexOf(path, "/", 3));
		} else {
			project = path.substring(StringUtils.ordinalIndexOf(path, "/", 2));
		}
		project = project.substring(project.indexOf("/")+1).replaceAll("[ ]?-[ ]?", "");

		try {

			resourceResolver = workItem.getMetaDataMap().get("resourceResolver", ResourceResolver.class);
			UserManager manager = resourceResolver.adaptTo(UserManager.class);

			if(metaDataMap.containsKey("PROCESS_ARGS")) {	
				processArgumentsArray = metaDataMap.get("PROCESS_ARGS", "string").toString().split(",");
			}

			for(String processArgument: processArgumentsArray) {
				if (processArgument.contains("requestApprovalTemplate")) {
					adminRequestTemplate = processArgument.substring(processArgument.indexOf(":") + 1);
				}
				if (processArgument.contains("adminRequestSubject")) {
					adminRequestSubject = processArgument.substring(processArgument.indexOf(":") + 1);
				}
				if (processArgument.contains("adminGroup")) {
					adminGroupKey = processArgument.substring(processArgument.indexOf(":") + 1);
				}
			}

			Configuration configuration = configurationAdmin.getConfiguration("com."+project+".workflowconfig");
			if (!configuration.equals(null)) {

				Dictionary<String, Object> properties = configuration.getProperties();
				if(!properties.equals(null)) {

					adminGroup = properties.get(adminGroupKey).toString();

					Node adminRequestTemplateNode = resourceResolver.getResource(adminRequestTemplate).adaptTo(Node.class);
					final Map<String, String> parametersAdminRequest = new HashMap<String, String>();
					parametersAdminRequest.put("initiator", workItem.getWorkflow().getInitiator());
					parametersAdminRequest.put("contentPath", workItem.getWorkflowData().getPayload().toString());

					//Get author-admin email
					String adminUserEmail = null;
					Authorizable authorAdminAuthorizable = manager.getAuthorizable(adminGroup);
					Group authorAdminGroup = (Group) authorAdminAuthorizable;
					Iterator authorAdmin = authorAdminGroup.getMembers();
					while(authorAdmin.hasNext()) {
						Object obj = authorAdmin.next();
						if(obj instanceof User) {
							User user = (User) obj;
							parametersAdminRequest.put("adminName", user.getID().toString());
							Authorizable userAuthorization = manager.getAuthorizable(user.getID());
							if(userAuthorization.hasProperty("profile/email")) {
								adminUserEmail = PropertiesUtil.toString(userAuthorization.getProperty("profile/email"), "");
								sendMail(adminRequestTemplate, adminRequestTemplateNode, parametersAdminRequest, adminRequestSubject, adminUserEmail);
							}
						}
					}
				}
			}

		} catch (RepositoryException | IOException | MessagingException | EmailException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		return adminGroup;

	}



	private ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
				session));
	}
	private void sendMail(String template, Node templateNode, Map<String, String> parameters, String subject, String emailAddress) 
			throws IOException, MessagingException, EmailException, RepositoryException {

		final MailTemplate mailTemplate = MailTemplate.create(template, templateNode.getSession());
		HtmlEmail email = mailTemplate.getEmail(StrLookup.mapLookup(parameters), HtmlEmail.class);
		email.setSubject(subject);
		email.addTo(emailAddress);
		MessageGateway<HtmlEmail> messageGateway = messageGatewayService.getGateway(HtmlEmail.class);
		messageGateway.send(email);

	}





}
