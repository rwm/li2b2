package de.sekmi.li2b2.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.li2b2.client.work.WorkplaceClient;
import de.sekmi.li2b2.client.crc.QueryClient;
import de.sekmi.li2b2.client.ont.OntologyClient;
import de.sekmi.li2b2.hive.Credentials;
import de.sekmi.li2b2.hive.DOMUtils;
import de.sekmi.li2b2.hive.HiveException;
import de.sekmi.li2b2.hive.HiveRequest;
import de.sekmi.li2b2.hive.pm.Cell;
import de.sekmi.li2b2.hive.pm.UserProject;
import de.sekmi.li2b2.client.pm.PMClient;
import de.sekmi.li2b2.client.pm.UserConfiguration;

/**
 * Client application to programmatically access any i2b2 server installation.
 * <p>
 * For simple initialisation/authentication, you can use the convenience 
 * method {@link #initializeClient(String, String, String, String, String, String)}
 * which returns a fully usable client instance.
 * </p>
 * <p>
 * Detailed initialisation is as follows: To connect to i2b2 webservices, 
 * call the following methods: {@link #setProxy(URL)},
 * {@link #setPM(URL)}, {@link #setAuthorisation(String, String, String, boolean)} and 
 * {@link #PM()}{@code .requestUserConfiguration()}. The returned {@link UserConfiguration}
 * must then be used for further configuration e.g. set the project via {@link #setProjectId(String)} 
 * and {@link #setServices(Cell[])} (with {@link UserConfiguration#getCells()}).
 * <p>
 * To find the values needed for the initial configuration, you can look at the contents 
 * of {@code /webclient/i2b2_config_data.js} of your target i2b2 server.
 * </p>
 * <p>
 * For a code example, see {@code src/test/java/de/sekmi/li2b2/client/TestClient.java}. See also 
 * the file {@code README.md}.
 * </p>
 * 
 * 
 * @author R.W.Majeed
 *
 */
public class Li2b2Client {
//	private static final Logger log = Logger.getLogger(Li2b2Client.class.getName());

	// configuration for connection
	private URL proxy;

	// information from server
	Credentials credentials;
	private String projectId;
	
	private PMClient pm;
	private OntologyClient ont;
	private QueryClient crc;
	private WorkplaceClient work;

	private Document requestTemplate;
	private DocumentBuilderFactory factory;
	
	private String outputEncoding;
	private MessageLogger messageLog;
	
	/**
	 * Construct a new client instance.
	 */
	public Li2b2Client(){
		factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		this.outputEncoding = "UTF-8";
		DocumentBuilder b;
		try {
			b = factory.newDocumentBuilder();
			requestTemplate = b.parse(getClass().getResourceAsStream("/request_template.xml"));
			DOMUtils.stripWhitespace(requestTemplate.getDocumentElement());
		} catch (ParserConfigurationException | SAXException | XPathExpressionException | IOException e) {
			throw new RuntimeException("Unable to load resource /request_template.xml", e);
		}
		// null project ID not allowed by hive.
		// it expects 'undefined' e.g. during login
		projectId = "undefined";
	}
	
	/** 
	 * Set the project id which will be used during hive communcations.
	 * @param projectId project ID
	 */
	public void setProjectId(String projectId){
		this.projectId = projectId;
	}
	/**
	 * Get the project ID
	 * @return project id
	 */
	public String getProjectId(){
		return this.projectId;
	}

	/**
	 * Use a reverse proxy configuration (proprietary protocol by i2b2) if
	 * the i2b2 webservices are not directly available via network.
	 * <p>
	 * If the webservices (e.g. /i2b2/services/PMService) are directly available,
	 * you don't need to use this method or can set the argument to {@code null}.
	 * </p>
	 * <p>
	 * If the application server (e.g. Wildfly) is listening only to {@code localhost},
	 * then you need to specify the reverse proxy script location. In i2b2 installations,
	 * this is commonly the PHP script at /webclient/index.php.
	 * If this method is used, you can then specify the local webservice address 
	 * via {@link #setPM(URL)} (e.g. {@code http://localhost:8080/i2b2/services/PMService/}). 
	 * </p>
	 * @param proxy URL to the i2b2 reverse proxy php script
	 */
	public void setProxy(URL proxy){
		this.proxy = proxy;
	}
	public URL getProxy(){
		return this.proxy;
	}
	public String getOutputEncoding(){
		return outputEncoding;
	}
	public String getUserLogin(){
		return credentials.getUser();
	}
	public String getUserDomain(){
		return credentials.getDomain();
	}
	/**
	 * Get the message logger. If no message
	 * logger is defined, {@code null} is returned.
	 * @return message logger or {@code null} if undefined.
	 */
	public MessageLogger getMessageLog(){
		return messageLog;
	}
	/**
	 * Set a message logger which will receive all sent and received messages.
	 * For console output, use {@link FormattedMessageLogger#consoleLogger()}.
	 *
	 * @param log message logger
	 */
	public void setMessageLog(MessageLogger log){
		this.messageLog = log;
	}
	/**
	 * Set login credentials for password based authentication.
	 * 
	 * @param user user name
	 * @param password password
	 * @param domain server domain name. The official i2b2 server exects this parameter to
	 *  match the domain name specified at the server.
	 * @param isToken whether specified password argument is a server session token or a password.
	 *  Set this to {@code false} if you are using a real password.
	 */
	@Deprecated
	public void setAuthorisation(String user, String password, String domain, boolean isToken){
		setCredentials(new Credentials(domain, user, password, isToken));
	}
	/**
	 * Set login credentials for password based authentication.
	 * This method is deprecated. Please use {@link #setCredentials(String, String, String)}.
	 * 
	 * @param user user name
	 * @param password password
	 * @param domain server domain name
	 */
	@Deprecated
	public void setAuthorisation(String user, String password, String domain){
		setAuthorisation(user, password, domain, false);
	}
	/**
	 * Set login credentials for password based authentication.
	 * This method is the same as {@link #setAuthorisation(String, String, String, boolean)} with
	 * the last argument set to {@code false}.
	 *
	 * @param user user name
	 * @param password password
	 * @param domain server domain name
	 */
	public void setCredentials(String domain, String user, String password) {
		setAuthorisation(user, password, domain, false);
	}
	public void setCredentials(Credentials credentials){
		this.credentials = credentials;
	}
	/**
	 * Get credentials used for all API calls to the server.
	 * @return credentials/token information
	 */
	public Credentials getCredentials() {
		return this.credentials;
	}
	DocumentBuilder newBuilder(){
		try {
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Set the PM service URL. The URL must be a complete URL including protocol scheme.
	 * <p>
	 * This url can be behind the reverse proxy if, if the reverse proxy was specified 
	 * via {@link #setProxy(URL)}. E.g. {@code http://localhost:9090/i2b2/services/PMService/}
	 * for some i2b2 demo VMs.
	 * </p>
	 * @param pmService PM service URL
	 */
	public void setPM(URL pmService){
		this.pm = new PMClient(this, pmService);
	}
	/**
	 * Set the Ontology Service URI. The URI is resolved using the PM service URL.
	 * Therefore relative paths or paths without scheme are ok 
	 * (e.g. {@code /i2b2/services/QueryToolService}).
	 * 
	 * @param uri URI to ontology service.
	 * @throws MalformedURLException for invalid URL strings
	 */
	public void setONT(String uri) throws MalformedURLException{
		this.ont = new OntologyClient(this, new URL(pm.serviceUrl, uri));
	}
	public void setCRC(String uri) throws MalformedURLException{
		this.crc = new QueryClient(this, new URL(pm.serviceUrl, uri));
	}
	public void setWORK(String uri) throws MalformedURLException{
		this.work = new WorkplaceClient(this, new URL(pm.serviceUrl, uri));
	}

	/**
	 * Get the PM cell client services.
	 * @return PM client services
	 */
	public PMClient PM(){
		return this.pm;
	}
	public OntologyClient ONT(){
		return this.ont;
	}
	public QueryClient CRC(){
		return this.crc;
	}
	public WorkplaceClient WORK(){
		return this.work;
	}

	protected HiveRequest createRequest(DocumentBuilder builder){
		Document req = builder.newDocument();
		req.appendChild(req.importNode(requestTemplate.getDocumentElement(), true));
		HiveRequest r = new HiveRequest(req);
		// TODO random message id
		r.setMessageId("asdf", "0");
		return r;
	}

	/**
	 * Initialize services using URIs from the provided Cell[] structure.
	 * XXX URI/URL exceptions are not thrown. instead a warning is logged.
	 * @param cells information about available cells
	 * @throws MalformedURLException for invalid cell URL strings
	 */
	public void setServices(Cell[] cells) throws MalformedURLException{
		for( int i=0; i<cells.length; i++ ){
			switch( cells[i].id ){
			case "ONT":
				setONT(cells[i].url);
				break;
			case "CRC":
				setCRC(cells[i].url);
				break;
			case "WORK":
				setWORK(cells[i].url);
				break;
			default:
//				log.info("Ignoring unsupported cell "+cells[i].id+": "+cells[i].name);
			}
		}
	}
//\\i2b2_LABS\i2b2\Labtests\
	/**
	 * Convenience method to obtain an initialized client instance. 
	 * Parameters {@code i2b2_proxy} and {@code i2b2_project} are optional.
	 * If no proxy is specified, a direct connection to the PM service is attempted.
	 * If no project is specified, it is assumed that the user has only a single project 
	 * available which's id is obtained from the server. If multiple projects are available,
	 * this method fails.
	 * @param i2b2_proxy i2b2 proxy endpoint. Commonly {@code https://yourserver/webclient/index.php}.
	 * @param i2b2_pm_service i2b2 project management service endpoint URL.
	 * @param i2b2_domain i2b2 domain name, needed for authentication
	 * @param i2b2_user i2b2 user name
	 * @param i2b2_pass password
	 * @param i2b2_project project name to use for further API calls. If not specified, determined from the server.
	 * @return initialized client instance
	 * @throws MalformedURLException malformed URL for i2b2_proxy or i2b2_pm_service
	 * @throws HiveException communication error with the server
	 */
	public static Li2b2Client initializeClient(String i2b2_proxy, String i2b2_pm_service, String i2b2_domain, String i2b2_user, String i2b2_pass, String i2b2_project) throws MalformedURLException, HiveException {
		Li2b2Client c = new Li2b2Client();
		if( i2b2_proxy != null ){
			c.setProxy(new URL(i2b2_proxy));			
		}
		c.setPM(new URL(i2b2_pm_service));
		c.setCredentials(i2b2_domain, i2b2_user, i2b2_pass);
		UserConfiguration uc = c.PM().requestUserConfiguration();
		if( i2b2_project == null ){
			UserProject[] projects = uc.getProjects();
			if( projects != null && projects.length == 1 ){
				// use first project
				c.setProjectId(projects[0].id);
			}else {
				// no or multiple projects available.
				StringBuilder b = new StringBuilder();
				for( int i=0; i<projects.length; i++ ) {
					if( i != 0 ) {
						b.append(", ");
					}
					b.append(projects[i].id);
				}
				throw new IllegalArgumentException("Auto project selection failed. Available projects: " + b.toString());
			}
		}else{
			c.setProjectId(i2b2_project);
		}
		// initialise other cells
		c.setServices(uc.getCells());
		return c;
	}
	public Document parseXML(InputStream in) throws IOException{
		try {
			return newBuilder().parse(in);
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}
	public Document parseXML(String document) throws IOException{
		try {
			return newBuilder().parse(new InputSource(new StringReader(document)));
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}
}
