package de.sekmi.li2b2.services;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.sekmi.li2b2.hive.DOMUtils;
import de.sekmi.li2b2.hive.HiveException;
import de.sekmi.li2b2.hive.HiveRequest;
import de.sekmi.li2b2.hive.HiveResponse;

public abstract class AbstractService extends AbstractCell{
	private static final Logger log = Logger.getLogger(AbstractService.class.getName());
	public static final String HIVE_NS="http://www.i2b2.org/xsd/hive/msg/1.1/";

	protected Document responseTemplate;
	
	protected AbstractService() throws HiveException{
		DocumentBuilder b;
		try {
			b = newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new HiveException(e);
		}
		responseTemplate = createResponseTemplate(b);
	}

	/**
	 * Service name (for communication to client).
	 * <p>
	 *  The default implementation returns {@link Class#getSimpleName()}.
	 * </p>
	 * @return service name, e.g. Workplace Cell
	 */
	public String getName(){
		return getClass().getSimpleName();
	}
	/**
	 * Service version (for communication to client)
	 * <p>
	 * The default implementation returns {@link Package#getImplementationVersion()}
	 * for the implementing class.
	 * </p>
	 * @return service version, e.g. 1.700
	 */
	public String getVersion(){
		return getClass().getPackage().getImplementationVersion();
	}
	
	DocumentBuilder newDocumentBuilder() throws ParserConfigurationException{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// use schema?
		//factory.setSchema(schema);
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder();
	}
	Document parseRequest(DocumentBuilder builder, InputStream requestBody) throws SAXException, IOException{
		Document dom = builder.parse(requestBody);
		// remove whitespace nodes from message header
		Element root = dom.getDocumentElement();
		try {
			DOMUtils.stripWhitespace(root);
		} catch (XPathExpressionException e) {
			log.log(Level.WARNING, "Unable to strip whitespace from request", e);
		}
		return dom;
	}
	protected HiveRequest parseRequest(InputStream requestBody) throws HiveException{
		try{
			DocumentBuilder b = newDocumentBuilder();
			Document dom = parseRequest(b, requestBody);
			HiveRequest req = new HiveRequest(dom);
			return req;
		}catch( IOException | SAXException | ParserConfigurationException e ){
			throw new HiveException("Error parsing request XML", e);
		}
	}

	protected HiveResponse createResponse(DocumentBuilder b, HiveRequest request){
		Document dom = b.newDocument();
		dom.appendChild(dom.importNode(responseTemplate.getDocumentElement(), true));
		
		HiveResponse resp = new HiveResponse(dom);
		resp.setTimestamp();
		
		// set sending application
		resp.setSendingApplication(getName(), getVersion());
		
		// set message id
		Element requestId = request.getMessageId();
		int msgInst;
		try{
			msgInst = Integer.parseInt(requestId.getLastChild().getTextContent());
			msgInst ++;
		}catch( NumberFormatException e ){
			msgInst = 1;
		}
		resp.setMessageId(requestId.getFirstChild().getTextContent(), Integer.toString(msgInst));
		resp.setProjectId(request.getProjectId());
		return resp;
	}
	
//	private void appendTextNode(Element el, String name, String value){
//		Element sub = (Element)el.appendChild(el.getOwnerDocument().createElement(name));
//		if( value != null ){
//			sub.appendChild(el.getOwnerDocument().createTextNode(value));
//		}
//	}
	private Document createResponseTemplate(DocumentBuilder builder) throws HiveException{
		Document dom;
		try{
			dom = builder.parse(getClass().getResourceAsStream("/response_template.xml"));
			DOMUtils.stripWhitespace(dom.getDocumentElement());
		} catch (SAXException | IOException | XPathExpressionException e) {
			throw new HiveException("Unable to load response template XML", e);
		}
		return dom;
	}
	/*
	Document createResponse(DocumentBuilder builder, Element request_header){
		Document dom = builder.newDocument();
		Element re = (Element)dom.appendChild(dom.createElementNS(HIVE_NS, "response"));
		NodeList nl;
		try {
			Document rh = builder.parse(getClass().getResourceAsStream("/templates/response_header.xml"));
			stripWhitespace(rh.getDocumentElement());
			// sending application
			nl = rh.getElementsByTagName("sending_application").item(0).getChildNodes();
			nl.item(0).setTextContent(getName());
			nl.item(1).setTextContent(getVersion());
			// timestamp
			rh.getElementsByTagName("datetime_of_message").item(0).setTextContent(Instant.now().toString());
			// security
			nl = rh.getElementsByTagName("security").item(0).getChildNodes();
			// TODO message_id, project, session
			re.appendChild(dom.adoptNode(rh.getDocumentElement()));
		} catch (SAXException | IOException | XPathExpressionException e) {
			log.log(Level.WARNING, "unable to process response header template", e);
		}
		
		return dom;
	}*/
	Element appendTextElement(Element parent, String name, String content){
		Element el = (Element)parent.getOwnerDocument().createElement(name);
		parent.appendChild(el);
		if( content != null ){
			el.setTextContent(content);
		}
		return el;
	}

}