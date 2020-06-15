package com.workflow.core.workflows;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.workflow.WorkflowException;

import com.day.cq.workflow.WorkflowSession;

import com.day.cq.workflow.exec.WorkItem;

import com.day.cq.workflow.exec.WorkflowProcess;

import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.mail.MessagingException;

import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;


@Component(service = WorkflowProcess.class,  
immediate = true, enabled = true,
property = {"process.label= Send Email Custom"})
public class CustomEmail implements WorkflowProcess 
{

	//Inject a MessageGatewayService 
	@Reference
	private MessageGatewayService messageGatewayService;

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void execute(WorkItem item, WorkflowSession wfsession,MetaDataMap args) throws WorkflowException {


		ResourceResolver resourceResolver = null;


		try
		{


			resourceResolver = getResourceResolver(wfsession.getSession());
			
			item.getMetaDataMap().put("resourceResolver", resourceResolver);
			UserManager manager = resourceResolver.adaptTo(UserManager.class);

			//Get Initiator email
			Authorizable authorizable = manager.getAuthorizable(item.getWorkflow().getInitiator());
			String initiatorEmail = PropertiesUtil.toString(authorizable.getProperty("profile/email"), "");


			String emailStatus = item.getNode().getDescription();

			if(emailStatus.equalsIgnoreCase("dev publish status") || emailStatus.equalsIgnoreCase("tech team approve status")) {

				String processArgumentsArray [] = null;
				if(args.containsKey("PROCESS_ARGS")) {	
					processArgumentsArray = args.get("PROCESS_ARGS", "string").toString().split(",");
				}

				String initiatorTemplate = null;
				String environment = null;
				String initiatorSubject = null;
				

				for(String processArgument: processArgumentsArray) {
					if (processArgument.contains("initiatorTemplate")) {
						initiatorTemplate = processArgument.substring(processArgument.indexOf(":") + 1);
					}
					if (processArgument.contains("environment")) {
						environment = processArgument.substring(processArgument.indexOf(":") + 1);
					}
					if (processArgument.contains("initiatorSubject")) {
						initiatorSubject = processArgument.substring(processArgument.indexOf(":") + 1);
					}
				}

				Node initiatorTemplateNode = getResourceResolver(wfsession.getSession()).getResource(initiatorTemplate).adaptTo(Node.class);
				final Map<String, String> parameters = new HashMap<String, String>();
				parameters.put("initiatorName", item.getWorkflow().getInitiator());
				parameters.put("contentPath", item.getWorkflowData().getPayload().toString());
				parameters.put("environment", environment);
				sendMail(initiatorTemplate, initiatorTemplateNode, parameters, initiatorSubject, initiatorEmail);

			}
			if(emailStatus.equalsIgnoreCase("author admin reject status") || emailStatus.equalsIgnoreCase("tech team reject status")) {

				String processArgumentsArray [] = null;
				if(args.containsKey("PROCESS_ARGS")) {	
					processArgumentsArray = args.get("PROCESS_ARGS", "string").toString().split(",");
				}
				String emailRejectTemplate = null;
				String rejectSubject = null;

				for(String processArgument: processArgumentsArray) {
					if (processArgument.contains("emailRejectTemplate")) {
						emailRejectTemplate = processArgument.substring(processArgument.indexOf(":") + 1);
					}
					if (processArgument.contains("rejectSubject")) {
						rejectSubject = processArgument.substring(processArgument.indexOf(":") + 1);
					}
				}
				Node emailRejectTemplateNode = getResourceResolver(wfsession.getSession()).getResource(emailRejectTemplate).adaptTo(Node.class);
				final Map<String, String> parameters = new HashMap<String, String>();
				parameters.put("initiatorName", item.getWorkflow().getInitiator().toString());
				parameters.put("contentPath", item.getWorkflowData().getPayload().toString());
				String approver = emailStatus.equalsIgnoreCase("author admin reject status") ? "author admin": "tech admin";
				parameters.put("approver", approver);
				sendMail(emailRejectTemplate, emailRejectTemplateNode, parameters, rejectSubject, initiatorEmail);

			}
		}

		catch (Exception e)
		{
			e.printStackTrace(); 
		}
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

	private ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
				session));
	}
}