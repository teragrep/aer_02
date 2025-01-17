#!/bin/bash

# Create CA cert
openssl genrsa -out ca-key.pem 2048
openssl req -new -x509 -nodes -days 100000 -key ca-key.pem -out ca-cert.pem -batch -subj '/CN=test-ca.example.com/C=FI'

# truststore, for both client and server
/usr/lib/jvm/jre-1.8.0-openjdk/bin/keytool -import -trustcacerts -alias "CA" -file ca-cert.pem -keystore truststore.jks -deststorepass changeit --noprompt

# ---------------- SERVER ----------------

# Server certificate
openssl req -newkey rsa:2048 -nodes -keyout server-key.pem -out server-req.pem -batch -subj '/CN=test-server.example.com/C=FI'
openssl x509 -req -days 10000 -in server-req.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out server-cert.pem
rm -f server-req.pem

# server keystore: combine pems
openssl pkcs12 -export -out combined-server.pfx -inkey server-key.pem -in server-cert.pem -passout pass:changeit

# server keystore: import combined pems
/usr/lib/jvm/jre-1.8.0-openjdk/bin/keytool -importkeystore -srckeystore combined-server.pfx -srcstoretype PKCS12 -srcstorepass changeit -deststorepass changeit -destkeypass changeit -destkeystore keystore-server.jks
rm -f combined-server.pfx

# ---------------- CLIENT ----------------

# client certificate
openssl req -newkey rsa:2048 -days 10000 -nodes -keyout client-key.pem -out client-req.pem -batch -subj '/CN=test-client.example.com/C=FI'
openssl x509 -req -days 10000 -in client-req.pem -CA ca-cert.pem -CAkey ca-key.pem -CAserial ca-cert.srl -out client-cert.pem
rm -f client-req.pem

# client keystore: combine pems
openssl pkcs12 -export -out combined-client.pfx -inkey client-key.pem -in client-cert.pem -passout pass:changeit

# client keystore: import combined pems
/usr/lib/jvm/jre-1.8.0-openjdk/bin/keytool -importkeystore -srckeystore combined-client.pfx -srcstoretype PKCS12 -srcstorepass changeit -deststorepass changeit -destkeypass changeit -destkeystore keystore-client.jks
rm -f combined-client.pfx
