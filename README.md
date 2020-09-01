# Secure File Server
With the Secure FileServer users can up and download files using their SSL client certificates for authentication. The FileServer is managed through a SOAP interface. Files can be downloaded over HTTPS using HTTP GET (Ranges are supported). Files can be uploaded using the tus protocol. The FileServer can also be used for Grote Berichten file transfer.  

## Overview
   
### Steps to configure a user and offer a file to download

1.  create a user with its SSL clientCertificate on the FileServer using the SOAP action createUser from the UserService  
2.  upload a file for the created user to the FileServer using the SOAP action uploadFile from the FileService and receive the path to the download file  
3.  the user can download the file from the FileServer in a browser using the path and is authenticated with its SSL certificate. Therefore the user must have its SSL keystore installed in the browser   
4.  Optional: download the external-data-reference for Grote Berichten file transfer  
    Note: The external-data-reference contains the full download url to the file

The FileServer uses SSL clientAuthentication, so the user must authenticate itself using an SSL key(/certificate) and the FileServer must trust this key (by trusting its certificate). Also the user must have its SSL keystore installed in the browser to download a file. The clientCertificate is registered to the user in the FileServer and will be used to authenticate the user when downloading a file. The FileServer can also operate behind a (reverse) proxy server.

See https://github.com/eluinstra/file-client for the FileClient.

### Install the FileServer

*   install JDK/JRE 8
*   create directory 'file-server'
*   copy file-server-1.0.0-M2 to file-server
*   cd file-server
*   create directory 'files'

### Start the FileServer

    java -Djavax.net.ssl.trustStore= -cp file-server-1.0.0-M2.jar dev.luin.file.server.StartGB -hsqldb

## Example

### Preparation

*   Download and install SoapUI to manage the FileServer
*   import file-server-soapui-project.xml into SoapUI (this project already contains some predefined SOAP Requests)
*   import keystore.p12 as certificate/keystore in your browser to be able to download the uploaded file from the FileServer (password: password)

### Usage

1.  create user user using certificate localhost.pem on the FileServer  
    run in SoapUI: file-server -> UserServiceSoapBinding -> createUser -> Create User user
2.  upload file license.txt for user user to the FileServer  
    run in SoapUI: file-server -> FileServiceSoapBinding -> uploadFile -> Upload File license.txt    
    The response contains the [path] of the file download in xpath://Envelope/Header/Body/uploadFileResponse/path
3.  download the file in your browser from the FileServer  
    open in your browser: https://localhost:8443/files/download[path]  
   	or use curl to download:

        curl -k --cert keystore.pem:password -I https://localhost:8443/files/download[path]

        curl -k --cert keystore.pem:password https://localhost:8443/files/download[path] -i

        curl -k --cert keystore.pem:password https://localhost:8443/files/download[path] -H "Range: bytes=0-255"
        curl -k --cert keystore.pem:password https://localhost:8443/files/download[path] -H "Range: bytes=256-511"
        curl -k --cert keystore.pem:password https://localhost:8443/files/download[path] -H "Range: bytes=512-763"  

        curl -k --cert keystore.pem:password https://localhost:8443/files/download[path] -i -H "Range: bytes=0-255, 256-511, 512-763"

4.  Optional: download Grote Berichten external-data-reference  
    run in SoapUI and use [path] in Request 1: file-server -> GBServiceSoapBinding -> getExternalDataReference -> Request 1
