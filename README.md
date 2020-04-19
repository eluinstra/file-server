Secured FileServer

Install the FileServer:
- install JDK/JRE 8
- create directory fs-service
- copy fs-service-1.0.0-SNAPSHOT to fs-service

Start the FileServer:
java -Djavax.net.ssl.trustStore= -cp fs-service-1.0.0-SNAPSHOT.jar org.bitbucket.eluinstra.fs.service.StartGB -hsqldb -soap -headless

Usage:
- Download and install SoapUI to manage the FileServer
- import fs-service-soapui-project.xml into SoapUI
- import keystore.p12 as certificate/keystore in your browser to be able to download the uploaded file from the FileServer

- create download client in the FileServer:
	run in SoapUI: fs-service -> FSAdminServiceImplServiceSoapBinding -> createClient -> Request 1
- upload file in the FileServer:
	run in SoapUI: fs-service -> FSServiceImplServiceSoapBinding -> uploadFile -> Request 1
- download the file in your browser from the FileServer:
	open in your browser: https://localhost:8443/fs<the value <virtualPath> from the uploadFile response>