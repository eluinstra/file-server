package dev.luin.file.server.web;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;

import javax.servlet.DispatcherType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.springframework.web.context.ContextLoaderListener;

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import dev.luin.file.server.core.KeyStoreManager.KeyStoreType;
import dev.luin.file.server.core.server.servlet.ClientCertificateAuthenticationFilter;
import dev.luin.file.server.core.server.servlet.ClientCertificateManagerFilter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class WebAuthentication implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		CLIENT_CERTIFICATE_HEADER("clientCertificateHeader"),
		AUTHENTICATION("authentication"),
		CLIENT_TRUST_STORE_TYPE("clientTrustStoreType"),
		CLIENT_TRUST_STORE_PATH("clientTrustStorePath"),
		CLIENT_TRUST_STORE_PASSWORD("clientTrustStorePassword");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		KEYSTORE_TYPE(KeyStoreType.PKCS12.name());

		String value;
	}

	private static final String REALM = "Realm";
	private static final String REALM_FILE = "realm.properties";
	TextIO textIO = TextIoFactory.getTextIO();
	CommandLine cmd;
	WebServer webServer;

	public static Options addOptions(Options result)
	{
		result.addOption(Option.CLIENT_CERTIFICATE_HEADER.name,true,"set client certificate header [default: " + NONE + "]");
		result.addOption(Option.AUTHENTICATION.name,false,"enable basic | client certificate authentication");
		result.addOption(Option.CLIENT_TRUST_STORE_TYPE.name,true,"set client truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.CLIENT_TRUST_STORE_PATH.name,true,"set client truststore path [default: " + NONE + "]");
		result.addOption(Option.CLIENT_TRUST_STORE_PASSWORD.name,true,"set client truststore password [default: " + NONE + "]");
		return result;
	}
	
	public Handler createContextHandler(ContextLoaderListener contextLoaderListener) throws NoSuchAlgorithmException, IOException
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + webServer.getWebConnectorName()});
		result.setInitParameter("configuration","deployment");
		result.setContextPath(webServer.getPath(cmd));
		if (cmd.hasOption(Option.AUTHENTICATION.name))
		{
			if (!webServer.isClientAuthenticationEnabled())
			{
				println("Configuring Web Server basic authentication:");
				val file = new File(REALM_FILE);
				if (file.exists())
					println("Using file " + file.getAbsoluteFile());
				else
					createRealmFile(file);
				result.setSecurityHandler(getSecurityHandler());
			}
			else if (webServer.isSSLEnabled() && webServer.isClientAuthenticationEnabled())
			{
				result.addFilter(createClientCertificateManagerFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
				result.addFilter(createClientCertificateAuthenticationFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
			}
		}
		result.addServlet(CXFServlet.class,webServer.getSoapPath() + "/*");
		result.setErrorHandler(createErrorHandler());
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected void createRealmFile(File file) throws IOException, NoSuchAlgorithmException
	{
		val username = textIO.newStringInputReader()
				.withDefaultValue("admin")
				.read("enter username");
		val password = readPassword();
		println("Writing to file: " + file.getAbsoluteFile());
		FileUtils.writeStringToFile(file,username + ": " + password + ",user",Charset.defaultCharset(),false);
	}

	private String readPassword() throws IOException, NoSuchAlgorithmException
	{
		val reader = textIO.newStringInputReader()
				.withMinLength(8)
				.withInputMasking(true);
		while (true)
		{
			val result = toMD5(reader.read("enter password"));
			val password = toMD5(reader.read("re-enter password"));
			if (result.equals(password))
				return result;
			else
				println("Passwords don't match! Try again.");
		}
	}
	
	private String toMD5(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		return "MD5:" + DigestUtils.md5Hex(s);
	}

	private SecurityHandler getSecurityHandler()
	{
		val result = new ConstraintSecurityHandler();
		val constraint = createSecurityConstraint();
		val mapping = createSecurityConstraintMapping(constraint);
		result.setConstraintMappings(Collections.singletonList(mapping));
		result.setAuthenticator(new BasicAuthenticator());
		result.setLoginService(new HashLoginService(REALM,REALM_FILE));
		return result;
	}

	private Constraint createSecurityConstraint()
	{
		val constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[]{"user","admin"});
		return constraint;
	}

	private ConstraintMapping createSecurityConstraintMapping(final Constraint constraint)
	{
		val mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);
		return mapping;
	}

	private ErrorPageErrorHandler createErrorHandler()
	{
		val result = new ErrorPageErrorHandler();
		val errorPages = new HashMap<String,String>();
		errorPages.put("404","/404");
		result.setErrorPages(errorPages);
		return result;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(CommandLine cmd)
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",cmd.getOptionValue(Option.CLIENT_CERTIFICATE_HEADER.name));
		return result;
	}

	protected FilterHolder createClientCertificateAuthenticationFilterHolder(CommandLine cmd) throws MalformedURLException, IOException
	{
		println("Configuring Web Server client certificate authentication:");
		val result = new FilterHolder(ClientCertificateAuthenticationFilter.class); 
		val clientTrustStoreType = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_TYPE.name,DefaultValue.KEYSTORE_TYPE.value);
		val clientTrustStorePath = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PATH.name);
		val clientTrustStorePassword = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PASSWORD.name);
		val trustStore = getResource(clientTrustStorePath);
		println("Using clientTrustStore " + trustStore.getURI());
		if (trustStore != null && trustStore.exists())
		{
			result.setInitParameter("trustStoreType",clientTrustStoreType);
			result.setInitParameter("trustStorePath",clientTrustStorePath);
			result.setInitParameter("trustStorePassword",clientTrustStorePassword);
			return result;
		}
		else
		{
			println("Web Server not available: clientTrustStore " + clientTrustStorePath + " not found!");
			exit(1);
			return null;
		}
	}
}
