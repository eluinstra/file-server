# Secured FileServer
With the Secured FileServer clients (users) can download files using their SSL client certificates for authentication. The FileServer is managed through a SOAP interface. The files can be downloaded over HTTPS using HTTP GET. The FileServer can be used for Grote Berichten file transfer.  

## Overview
   
### Steps to configure a client and offer a file download

1.  create a client with its SSL clientCertificate on the FileServer using the SOAP action createClient from the FSAdminService  
2.  upload a file for the created client to the FileServer using the SOAP action uploadFile from the FSService and receive the virtualPath to the download file  
3.  the client can download the file from the FileServer in a browser using the virtualPath and is authenticated with its SSL certificate. Therefore the client must have its SSL keystore installed in the browser   
4.  Optional: download the external-data-reference for Grote Berichten file transfer  
    Note: The external-data-reference contains the full download path to the file

The FileServer uses SSL client authentication, so the client must authenticate itself using an SSL key(/certificate) and the FileServer must trust this key (by trusting its certificate). Also the client must have its SSL keystore installed in the browser to download a file. The clientCertificate is registered to the client in the FileServer and will be used to authenticate the client when downloading a file.

### Install the FileServer

*   install JDK/JRE 8
*   create directory fs-service
*   copy fs-service-1.0.0-SNAPSHOT to fs-service

### Start the FileServer

    java -Djavax.net.ssl.trustStore= -cp fs-service-1.0.0-SNAPSHOT.jar org.bitbucket.eluinstra.fs.service.StartGB -hsqldb -soap -headless

## Example

### Preparation

*   Download and install SoapUI to manage the FileServer
*   import fs-service-soapui-project.xml into SoapUI (this project already contains some predefined SOAP Requests)
*   import keystore.p12 as certificate/keystore in your browser to be able to download the uploaded file from the FileServer

### Usage

1.  create client Ordina using certificate localhost.pem on the FileServer  
    run in SoapUI: fs-service -> FSAdminServiceImplServiceSoapBinding -> createClient -> create downloadClient Ordina
2.  upload file hsqldb.create.sql for downloadClient Ordina to the FileServer  
    run in SoapUI: fs-service -> FSServiceImplServiceSoapBinding -> uploadFile -> upload file hsqldb.create.sql    
    The response contains the virtualPath of the file download in xpath://Envelope/Header/Body/uploadFileResponse/fsFile/virtualPath
3.  download the file in your browser from the FileServer  
    open in your browser: https://localhost:8443/fs[virtualPath]
4.  Optional: download Grote Berichten external-data-reference  
    run in SoapUI and use [virtualPath] in Request 1: fs-service -> GBServiceImplServiceSoapBinding -> getExternalDataReference -> Request 1
