# Secure File Server
With the Secure FileServer users can up and download files using their SSL client certificates for authentication. The FileServer is managed through a SOAP interface. Files can be downloaded over HTTPS using HTTP GET (Ranges are supported). Files can be uploaded using the tus protocol. The FileServer can also be used for Grote Berichten file transfer.  

## Overview
   
### Steps to configure a user and offer a file to download

1. create a user with its SSL clientCertificate on the FileServer using the SOAP action createUser from the UserService  
2. upload a file for the created user to the FileServer using the SOAP action uploadFile from the FileService and receive the path to the download file  
3. the user can download the file from the FileServer in a browser using the path and is authenticated with its SSL certificate. Therefore the user must have its SSL keystore installed in the browser   
4. Optional: download the external-data-reference for Grote Berichten file transfer  
   Note: The external-data-reference contains the full download url to the file

The FileServer uses SSL clientAuthentication, so the user must authenticate itself using an SSL key(/certificate) and the FileServer must trust this key (by trusting its certificate). Also the user must have its SSL keystore installed in the browser to download a file. The clientCertificate is registered to the user in the FileServer and will be used to authenticate the user when downloading a file. The FileServer can also operate behind a (reverse) proxy server.

See https://github.com/eluinstra/file-client for the FileClient.

### Prerequisites

- install JDK/JRE 11
- download and install SoapUI
- download [file-server-1.0.0](https://github.com/eluinstra/file-server/releases/download/1.0.0/file-server-1.0.0.jar)
- download [file-client-1.0.0](https://github.com/eluinstra/file-client/releases/download/1.0.0/file-client-1.0.0.jar)
- download [file-server-soapui-project.xml](https://github.com/eluinstra/file-server/raw/master/resources/file-server-soapui-project.xml)
- download [file-client-soapui-project.xml](https://github.com/eluinstra/file-client/raw/master/resources/file-client-soapui-project.xml)

### Install the FileServer

- create directory `file-server`
- copy file-server-1.0.0 to file-server
- cd file-server
- create directory `files`

### Install the FileClient

- create directory `file-client`
- copy file-client-1.0.0 to file-client
- cd file-client
- create directory `files`

### Start the FileServer

```
java -cp file-server-1.0.0.jar dev.luin.file.server.StartGB -hsqldb
```

### Start the FileServer

```
java -cp file-client-1.0.0.jar dev.luin.file.client.StartGB -hsqldb -port 8000
```

## Example

### Preparation

- Download and install SoapUI to manage the FileServer and FileClient
- import [file-server-1.0.0](https://github.com/eluinstra/file-server/releases/download/1.0.0/file-server-1.0.0.jar) and [file-client-1.0.0](https://github.com/eluinstra/file-client/releases/download/1.0.0/file-client-1.0.0.jar) into SoapUI (these projects already contain some predefined SOAP Requests)
- Start the FileServer and FileClient

### Usage

1. create user `user` (using certificate `localhost.pem`) on the FileServer  
   run in SoapUI `file-server -> UserServiceSoapBinding -> createUser -> Create User user`

2. upload file `Lorem Ipsum.txt` for user `user` to the FileServer  
   run in SoapUI `file-server -> FileServiceSoapBinding -> uploadFile -> Upload Lorem Ipsum.txt`  
   The response contains the `path` of the file download in `xpath://Envelope/Header/Body/uploadFileResponse/path`
3. download Grote Berichten external-data-reference  
   run in SoapUI using `path` from step 2 in `file-server -> GBServiceSoapBinding -> getExternalDataReference -> Request 1`
   The response contains the `URL` of the file download in `xpath://Envelope/Header/Body/getExternalDataReferenceResponse/external-data-reference/data-reference/transport/location/senderUrl`

4. download the file `Lorem Ipsum.txt` from the FileServer using the FileClient
   run in SoapUI using `URL` from step 3 in `file-client -> FileServiceSoapBinding -> downloadFile -> Request 1`  
5. download the file `Lorem Ipsum.txt` from the FileClient
   run in SoapUI `file-client -> FileServiceSoapBinding -> getFile -> Request 1`  

6. upload the file `Mauris nisl.txt` to the FileServer using the FileClient
   run in SoapUI `file-client -> FileServiceSoapBinding -> uploadFile -> Upload Mauris nisl.txt`  
7. download Grote Berichten external-data-reference  
   run in SoapUI `file-client -> GBServiceSoapBinding -> getExternalDataReference -> Request 1`
   The response contains the `URL` of the file download in `xpath://Envelope/Header/Body/getExternalDataReferenceResponse/external-data-reference/data-reference/transport/location/receiverUrl`

8. download the file `Mauris nisl.txt` from the FileServer
   run in SoapUI using the path portion from `URL` (so minus the base upload portion https://localhost:8443/files/upload) from step 7 as `path` in `file-server -> FileServiceSoapBinding -> downloadFile -> Request 1`  
