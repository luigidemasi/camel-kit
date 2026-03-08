## Red Hat build of Apache Camel

Format Multi-page Single-page View full doc as PDF

## Red Hat build of Apache Camel

1. Quarkus CXF Security Guide for Red Hat build of Apache Camel
2. [Preface](#idm140236923695616)
3. 1. Quarkus CXF security guide
4. 2. Camel Security
5. [Legal Notice](#idm140236916039760)

Format Multi-page Single-page View full doc as PDF

# Quarkus CXF Security Guide for Red Hat build of Apache Camel

Red Hat build of Apache Camel 4.8

## Quarkus CXF Security Guide for Red Hat build of Apache Camel provided by Red Hat

[Legal Notice](#idm140236916039760)

**Abstract**

The TitleCQSecurityGuide describes security aspects of Quarkus XXF extensions supported by Red Hat.

## [Preface Copy link](#idm140236923695616)

### Providing feedback on Red Hat build of Apache Camel documentation

To report an error or to improve our documentation, log in to your Red Hat Jira account and submit an issue. If you do not have a Red Hat Jira account, then you will be prompted to create an account.

**Procedure**

1. Click the following link to [create ticket](https://issues.redhat.com/secure/CreateIssueDetails!init.jspa?pid=12342723&summary=%5Buser+feeedback+via+link%5D+&issuetype=1&description=%5BPlease+include+the+Document+URL+the+section+number+and+describe+the+issue%5D&priority=3&labels=%5Bcustomer-feedback%5D&components=12395440)
2. Enter a brief description of the issue in the Summary.
3. Provide a detailed description of the issue or enhancement in the Description. Include a URL to where the issue occurs in the documentation.
4. Clicking Submit creates and routes the issue to the appropriate documentation team.

## [Chapter 1. Quarkus CXF security guide Copy link](#quarkus-cxf-reference-intro-quarkus-cxf-security-guide)

This chapter provides information about security when working with Quarkus CXF extensions.

### [1.1. Security guide Copy link](#security-guide-index-quarkus-cxf-security-guide)

The security guide documents various security related aspects of Quarkus CXF:

- [SSL, TLS and HTTPS](#ssl-tls-https)
- [Authentication and authorization](#authentication-authorization)
- [Authentication enforced by WS-SecurityPolicy](#ws-securitypolicy-authentication-authorization)

#### [1.1.1. SSL, TLS and HTTPS Copy link](#ssl-tls-https)

This section documents various use cases related to SSL, TLS and HTTPS.

Note

The sample code snippets used in this section come from the [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) in the source tree of Quarkus CXF

##### [1.1.1.1. Client SSL configuration Copy link](#client_ssl_configuration)

If your client is going to communicate with a server whose SSL certificate is not trusted by the client's operating system, then you need to set up a custom trust store for your client.

Tools like `openssl` or Java `keytool` are commonly used for creating and maintaining truststores.

We have examples for both tools in the Quarkus CXF source tree:

- [Create truststore with Java 'keytool' (wrapped by a Maven plugin)](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/pom.xml#L185-L520)
- [Create truststore with](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/generate-certs.sh) [`openssl`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy/generate-certs.sh)

Once you have prepared the trust store, you need to configure your client to use it.

##### [1.1.1.1.1. Set the client trust store in application.properties Copy link](#set_the_client_trust_store_in_literal_application_properties_literal)

This is the easiest way to set the client trust store. The key role is played by the following properties:

- [quarkus.cxf.client."client-name".trust-store](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store)
- [quarkus.cxf.client."client-name".trust-store-type](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store-type)
- [quarkus.cxf.client."client-name".trust-store-password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-trust-store-password)

Here is an example:

**application.properties**

```
# Client side SSL
quarkus.cxf.client.hello.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/hello
quarkus.cxf.client.hello.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
```

1

```
quarkus.cxf.client.hello.trust-store-type = pkcs12
```

2

```
quarkus.cxf.client.hello.trust-store = client-truststore.pkcs12
quarkus.cxf.client.hello.trust-store-password = client-truststore-password
```

Copy to Clipboard

Toggle word wrap

[1](#CO1-1)

`pkcs12` and `jks` are two commonly used keystore formats. PKCS12 is the [default Java keystore format](https://openjdk.org/jeps/229) since Java 9. We recommend using PKCS12 rather than JKS, because it offers stronger cryptographic algorithms, it is extensible, standardized, language-neutral and widely supported. [2](#CO1-2) The referenced `client-truststore.pkcs12` file has to be available either in the classpath or in the file system.

##### [1.1.1.2. Server SSL configuration Copy link](#server_ssl_configuration)

To make your services available over the HTTPS protocol, you need to set up server keystore in the first place. The server SSL configuration is driven by Vert.x, the HTTP layer of Quarkus. {link-quarkus-docs-base}/http-reference#ssl[Quarkus HTTP guide] provides the information about the configuration options.

Here is a basic example:

**application.properties**

```
# Server side SSL
quarkus.tls.key-store.p12.path = localhost-keystore.pkcs12
quarkus.tls.key-store.p12.password = localhost-keystore-password
quarkus.tls.key-store.p12.alias = localhost
quarkus.tls.key-store.p12.alias-password = localhost-keystore-password
```

Copy to Clipboard

Toggle word wrap

##### [1.1.1.3. Mutual TLS (mTLS) authentication Copy link](#mtls-quarkus-cxf-security-guide)

So far, we have explained the simple or single-sided case where only the server proves its identity through an SSL certificate and the client has to be set up to trust that certificate. Mutual TLS authentication goes by letting also the client prove its identity using the same means of public key cryptography.

Hence, for the Mutual TLS (mTLS) authentication, in addition to setting up the server keystore and client truststore as described above, you need to set up the keystore on the client side and the truststore on the server side.

The tools for creating and maintaining the stores are the same and the configuration properties to use are pretty much analogous to the ones used in the Simple TLS case.

The [mTLS integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls) in the Quarkus CXF source tree can serve as a good starting point.

The keystores and truststores are created with [`openssl`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/generate-certs.sh) (or alternatively with Java [Java](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/pom.xml#L140-L408) [`keytool`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/mtls/pom.xml#L140-L408) )

Here is the `application.properties` file:

**application.properties**

```
# Server keystore for Simple TLS
quarkus.tls.localhost-pkcs12.key-store.p12.path = localhost-keystore.pkcs12
quarkus.tls.localhost-pkcs12.key-store.p12.password = localhost-keystore-password
quarkus.tls.localhost-pkcs12.key-store.p12.alias = localhost
quarkus.tls.localhost-pkcs12.key-store.p12.alias-password = localhost-keystore-password
# Server truststore for Mutual TLS
quarkus.tls.localhost-pkcs12.trust-store.p12.path = localhost-truststore.pkcs12
quarkus.tls.localhost-pkcs12.trust-store.p12.password = localhost-truststore-password
# Select localhost-pkcs12 as the TLS configuration for the HTTP server
quarkus.http.tls-configuration-name = localhost-pkcs12

# Do not allow any clients which do not prove their indentity through an SSL certificate
quarkus.http.ssl.client-auth = required

# CXF service
quarkus.cxf.endpoint."/mTls".implementor = io.quarkiverse.cxf.it.auth.mtls.MTlsHelloServiceImpl

# CXF client with a properly set certificate for mTLS
quarkus.cxf.client.mTls.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/mTls
quarkus.cxf.client.mTls.service-interface = io.quarkiverse.cxf.it.security.policy.HelloService
quarkus.cxf.client.mTls.key-store = target/classes/client-keystore.pkcs12
quarkus.cxf.client.mTls.key-store-type = pkcs12
quarkus.cxf.client.mTls.key-store-password = client-keystore-password
quarkus.cxf.client.mTls.key-password = client-keystore-password
quarkus.cxf.client.mTls.trust-store = target/classes/client-truststore.pkcs12
quarkus.cxf.client.mTls.trust-store-type = pkcs12
quarkus.cxf.client.mTls.trust-store-password = client-truststore-password

# Include the keystores in the native executable
quarkus.native.resources.includes = *.pkcs12,*.jks
```

Copy to Clipboard

Toggle word wrap

##### [1.1.1.4. Enforce SSL through WS-SecurityPolicy Copy link](#enforce_ssl_through_ws_securitypolicy)

The requirement for the clients to connect through HTTPS can be defined in a policy.

The functionality is provided by [`quarkus-cxf-rt-ws-security`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#-rt-ws-security) extension.

Here is an example of a policy file:

**https-policy.xml**

```
<?xml version="1.0" encoding="UTF-8"?>
<wsp:Policy wsp:Id="HttpsSecurityServicePolicy"
            xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
    xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <wsp:ExactlyOne>
        <wsp:All>
            <sp:TransportBinding>
                <wsp:Policy>
                    <sp:TransportToken>
                        <wsp:Policy>
                            <sp:HttpsToken RequireClientCertificate="false" />
                        </wsp:Policy>
                    </sp:TransportToken>
                    <sp:IncludeTimestamp />
                    <sp:AlgorithmSuite>
                        <wsp:Policy>
                            <sp:Basic128 />
                        </wsp:Policy>
                    </sp:AlgorithmSuite>
                </wsp:Policy>
            </sp:TransportBinding>
        </wsp:All>
    </wsp:ExactlyOne>
</wsp:Policy>
```

Copy to Clipboard

Toggle word wrap

The policy has to be referenced from a service endpoint interface (SEI):

**HttpsPolicyHelloService.java**

```
package io.quarkiverse.cxf.it.security.policy;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.apache.cxf.annotations.Policy;

/**
 * A service implementation with a transport policy set
 */
@WebService(serviceName = "HttpsPolicyHelloService")
@Policy(placement = Policy.Placement.BINDING, uri = "https-policy.xml")
public interface HttpsPolicyHelloService extends AbstractHelloService {

    @WebMethod
    @Override
    public String hello(String text);

}
```

Copy to Clipboard

Toggle word wrap

With this setup in place, any request delivered over HTTP will be rejected by the `PolicyVerificationInInterceptor` :

```
ERROR [org.apa.cxf.ws.pol.PolicyVerificationInInterceptor] Inbound policy verification failed: These policy alternatives can not be satisfied:
 {http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702}TransportBinding: TLS is not enabled
 ...
```

Copy to Clipboard

Toggle word wrap

#### [1.1.2. Authentication and authorization Copy link](#authentication-authorization)

Note

The sample code snippets shown in this section come from the [Client and server integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/client-server) in the source tree of Quarkus CXF. You may want to use it as a runnable example.

##### [1.1.2.1. Client HTTP basic authentication Copy link](#client-http-basic-authentication)

Use the following client configuration options provided by [`quarkus-cxf`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#) extension to pass the username and password for HTTP basic authentication:

- [quarkus.cxf.client."client-name".username](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-username)
- [quarkus.cxf.client."client-name".password](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-password)

Here is an example:

**application.properties**

```
quarkus.cxf.client.basicAuth.wsdl = http://localhost:${quarkus.http.test-port}/soap/basicAuth?wsdl
quarkus.cxf.client.basicAuth.client-endpoint-url = http://localhost:${quarkus.http.test-port}/soap/basicAuth
quarkus.cxf.client.basicAuth.username = bob
quarkus.cxf.client.basicAuth.password = bob234
```

Copy to Clipboard

Toggle word wrap

##### [1.1.2.1.1. Accessing WSDL protected by basic authentication Copy link](#accessing_wsdl_protected_by_basic_authentication)

By default, the clients created by Quarkus CXF do not send the `Authorization` header, unless you set the [`quarkus.cxf.client."client-name".secure-wsdl-access`](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#quarkus_cxf-quarkus-cxf-client-client-name-secure-wsdl-access) to `true` :

**application.properties**

```
quarkus.cxf.client.basicAuthSecureWsdl.wsdl = http://localhost:${quarkus.http.test-port}/soap/basicAuth?wsdl
quarkus.cxf.client.basicAuthSecureWsdl.client-endpoint-url = http://localhost:${quarkus.http.test-port}/soap/basicAuthSecureWsdl
quarkus.cxf.client.basicAuthSecureWsdl.username = bob
quarkus.cxf.client.basicAuthSecureWsdl.password = ${client-server.bob.password}
quarkus.cxf.client.basicAuthSecureWsdl.secure-wsdl-access = true
```

Copy to Clipboard

Toggle word wrap

##### [1.1.2.2. Mutual TLS (mTLS) authentication Copy link](#mutual_tls_mtls_authentication)

See the [Mutual TLS (mTLS) authentication](#mtls-quarkus-cxf-security-guide) section in SSL, TLS and HTTPS guide.

##### [1.1.2.3. Securing service endpoints Copy link](#securing-service-endpoints-quarkus-cxf-security-guide)

The server-side authentication and authorization is driven by {link-quarkus-docs-base}/security-overview[Quarkus Security], especially when it comes to

- {link-quarkus-docs-base}/security-authentication-mechanisms[Authentication mechanisms]
- {link-quarkus-docs-base}/security-identity-providers[Identity providers]
- {link-quarkus-docs-base}/security-authorize-web-endpoints-reference[Role-based access control (RBAC)]

There is a basic example in our [Client and server integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/client-server) . Its key parts are:

- `io.quarkus:quarkus-elytron-security-properties-file` dependency as an Identity provider
- Basic authentication enabled and users with their roles configured in `application.properties` : **application.properties** `quarkus.http.auth.basic = true quarkus.security.users.embedded.enabled = true quarkus.security.users.embedded.plain-text = true quarkus.security.users.embedded.users.alice = alice123 quarkus.security.users.embedded.roles.alice = admin quarkus.security.users.embedded.users.bob = bob234 quarkus.security.users.embedded.roles.bob = app-user` Copy to Clipboard Toggle word wrap
- Role-based access control enfoced via `@RolesAllowed` annotation:

**BasicAuthHelloServiceImpl.java**

```
package io.quarkiverse.cxf.it.auth.basic;

import jakarta.annotation.security.RolesAllowed;
import jakarta.jws.WebService;

import io.quarkiverse.cxf.it.HelloService;

@WebService(serviceName = "HelloService", targetNamespace = HelloService.NS)
@RolesAllowed("app-user")
public class BasicAuthHelloServiceImpl implements HelloService {
    @Override
    public String hello(String person) {
        return "Hello " + person + "!";
    }
}
```

Copy to Clipboard

Toggle word wrap

#### [1.1.3. Authentication enforced by WS-SecurityPolicy Copy link](#ws-securitypolicy-authentication-authorization)

You can enforce authentication through WS-SecurityPolicy, instead of [Mutual TLS](#mtls-quarkus-cxf-security-guide) and Basic HTTP authentication for [clients](#client-http-basic-authentication) and [services](#securing-service-endpoints-quarkus-cxf-security-guide) .

To enforce authentication through WS-SecurityPolicy, follow these steps:

1. Add a supporting tokens policy to an endpoint in the WSDL contract.
2. On the server side, implement an authentication callback handler and associate it with the endpoint in `application.properties` or via environment variables. Credentials received from clients are authenticated by the callback handler.
3. On the client side, provide credentials through either configuration in `application.properties` or environment variables. Alternatively, you can implement an authentication callback handler to pass the credentials.

##### [1.1.3.1. Specifying an Authentication Policy Copy link](#Auth-Policy)

If you want to enforce authentication on a service endpoint, associate a *supporting tokens* policy assertion with the relevant endpoint binding and specify one or more *token assertions* under it.

There are several different kinds of supporting tokens policy assertions, whose XML element names all end with `SupportingTokens` (for example, `SupportingTokens` , `SignedSupportingTokens` , and so on). For a complete list, see the [Supporting Tokens](https://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/ws-securitypolicy-1.2-spec-os.html#_Toc161826561) section of the WS-SecurityPolicy specification.

##### [1.1.3.2. UsernameToken policy assertion example Copy link](#literal_usernametoken_literal_policy_assertion_example)

Tip

The sample code snippets used in this section come from the [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) in the source tree of Quarkus CXF. You may want to use it as a runnable example.

The following listing shows an example of a policy that requires a WS-Security `UsernameToken` (which contains username/password credentials) to be included in the security header.

**username-token-policy.xml**

```
<?xml version="1.0" encoding="UTF-8"?>
<wsp:Policy
        wsp:Id="UsernameTokenSecurityServicePolicy"
        xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
    xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
    xmlns:sp13="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200802"
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <wsp:ExactlyOne>
        <wsp:All>
            <sp:SupportingTokens>
                <wsp:Policy>
                    <sp:UsernameToken
                        sp:IncludeToken="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/AlwaysToRecipient">
                        <wsp:Policy>
                            <sp:WssUsernameToken11 />
                            <sp13:Created />
                            <sp13:Nonce />
                        </wsp:Policy>
                    </sp:UsernameToken>
                </wsp:Policy>
            </sp:SupportingTokens>
        </wsp:All>
    </wsp:ExactlyOne>
</wsp:Policy>
```

Copy to Clipboard

Toggle word wrap

There are two ways how you can associate this policy file with a service endpoint:

- Reference the policy on the Service Endpoint Interface (SEI) like this: **UsernameTokenPolicyHelloService.java** `@WebService(serviceName = "UsernameTokenPolicyHelloService") @Policy(placement = Policy.Placement.BINDING, uri = "username-token-policy.xml") public interface UsernameTokenPolicyHelloService extends AbstractHelloService { ... }` Copy to Clipboard Toggle word wrap
- Include the policy [in your WSDL contract](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L163) and reference it via [`PolicyReference`](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) [element](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-trust/src/main/resources/ws-trust-1.4-service.wsdl#L95) .

When you have the policy in place, configure the credentials on the service endpoint and the client:

**application.properties**

```
# A service with a UsernameToken policy assertion
quarkus.cxf.endpoint."/helloUsernameToken".implementor = io.quarkiverse.cxf.it.security.policy.UsernameTokenPolicyHelloServiceImpl
quarkus.cxf.endpoint."/helloUsernameToken".security.callback-handler = #usernameTokenPasswordCallback

# These properties are used in UsernameTokenPasswordCallback
# and in the configuration of the helloUsernameToken below
wss.user = cxf-user
wss.password = secret

# A client with a UsernameToken policy assertion
quarkus.cxf.client.helloUsernameToken.client-endpoint-url = https://localhost:${quarkus.http.test-ssl-port}/services/helloUsernameToken
quarkus.cxf.client.helloUsernameToken.service-interface = io.quarkiverse.cxf.it.security.policy.UsernameTokenPolicyHelloService
quarkus.cxf.client.helloUsernameToken.security.username = ${wss.user}
quarkus.cxf.client.helloUsernameToken.security.password = ${wss.password}
```

Copy to Clipboard

Toggle word wrap

In the above listing, `usernameTokenPasswordCallback` is a name of a `@jakarta.inject.Named` bean implementing `javax.security.auth.callback.CallbackHandler` . Quarkus CXF will lookup a bean with this [name](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.8/html-single/red_hat_build_of_apache_camel_for_quarkus_reference/#beanRefs) in the CDI container.

Here is an example implementation of the bean:

**UsernameTokenPasswordCallback.java**

```
package io.quarkiverse.cxf.it.security.policy;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("usernameTokenPasswordCallback") /* We refer to this bean by this name from application.properties */
public class UsernameTokenPasswordCallback implements CallbackHandler {

    /* These two configuration properties are set in application.properties */
    @ConfigProperty(name = "wss.password")
    String password;
    @ConfigProperty(name = "wss.user")
    String user;

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        if (callbacks.length < 1) {
            throw new IllegalStateException("Expected a " + WSPasswordCallback.class.getName()
                    + " at possition 0 of callbacks. Got array of length " + callbacks.length);
        }
        if (!(callbacks[0] instanceof WSPasswordCallback)) {
            throw new IllegalStateException(
                    "Expected a " + WSPasswordCallback.class.getName() + " at possition 0 of callbacks. Got an instance of "
                            + callbacks[0].getClass().getName() + " at possition 0");
        }
        final WSPasswordCallback pc = (WSPasswordCallback) callbacks[0];
        if (user.equals(pc.getIdentifier())) {
            pc.setPassword(password);
        } else {
            throw new IllegalStateException("Unexpected user " + user);
        }
    }

}
```

Copy to Clipboard

Toggle word wrap

To test the whole setup, you can create a simple `{link-quarkus-docs-base}/getting-started-testing[@QuarkusTest]` :

**UsernameTokenTest.java**

```
package io.quarkiverse.cxf.it.security.policy;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UsernameTokenTest {

    @CXFClient("helloUsernameToken")
    UsernameTokenPolicyHelloService helloUsernameToken;

    @Test
    void helloUsernameToken() {
        Assertions.assertThat(helloUsernameToken.hello("CXF")).isEqualTo("Hello CXF from UsernameToken!");
    }
}
```

Copy to Clipboard

Toggle word wrap

When running the test via `mvn test -Dtest=UsernameTokenTest` , you should see a SOAP message being logged with a `Security` header containing `Username` and `Password` :

**Log output of the UsernameTokenTest**

```
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" soap:mustUnderstand="1">
      <wsse:UsernameToken xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" wsu:Id="UsernameToken-bac4f255-147e-42a4-aeec-e0a3f5cd3587">
        <wsse:Username>cxf-user</wsse:Username>
        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">secret</wsse:Password>
        <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">3uX15dZT08jRWFWxyWmfhg==</wsse:Nonce>
        <wsu:Created>2024-10-02T17:32:10.497Z</wsu:Created>
      </wsse:UsernameToken>
    </wsse:Security>
  </soap:Header>
  <soap:Body>
    <ns2:hello xmlns:ns2="http://policy.security.it.cxf.quarkiverse.io/">
      <arg0>CXF</arg0>
    </ns2:hello>
  </soap:Body>
</soap:Envelope>
```

Copy to Clipboard

Toggle word wrap

##### [1.1.3.3. SAML v1 and v2 policy assertion examples Copy link](#saml_v1_and_v2_policy_assertion_examples)

The [WS-SecurityPolicy integration test](https://github.com/quarkiverse/quarkus-cxf/tree/main/integration-tests/ws-security-policy) contains also analogous examples with SAML v1 and SAML v2 assertions.

## [Chapter 2. Camel Security Copy link](#camel-security)

This chapter provides information about Camel route security options.

### [2.1. Camel security overview Copy link](#camel-security-overview)

Camel offers several forms &amp; levels of security capabilities that can be utilized on Camel routes. These various forms of security may be used in conjunction with each other or separately.

The broad categories offered are:

- *Route Security* - Authentication and Authorization services to proceed on a route or route segment
- *Payload Security* - Data Formats that offer encryption/decryption services at the payload level
- *Endpoint Security* - Security offered by components that can be utilized by endpointUri associated with the component
- *Configuration Security* - Security offered by encrypting sensitive information from configuration files or external Secured Vault systems.

Camel offers the [JSSE Utility](https://rhaetor.github.io/rh-camel/manual/camel-configuration-utilities.html) for configuring SSL/TLS related aspects of a number of Camel components.

### [2.2. Route Security Copy link](#route_security)

Authentication and Authorization Services

Camel offers [Route Policy](https://rhaetor.github.io/rh-camel/manual/route-policy.html) driven security capabilities that may be wired into routes or route segments. A route policy in Camel utilizes a strategy pattern for applying interceptors on Camel Processors. It's offering the ability to apply cross-cutting concerns (for example. security, transactions etc) of a Camel route.

### [2.3. Payload Security Copy link](#payload_security)

Camel offers encryption/decryption services to secure payloads or selectively apply encryption/decryption capabilities on portions/sections of a payload.

The dataformats offering encryption/decryption of payloads utilizing [Marshal](https://rhaetor.github.io/rh-camel/components/4.8.x//eips/marshal-eip.html) are:

- [Crypto](https://rhaetor.github.io/rh-camel/components/4.8.x//dataformats/crypto-dataformat.html)
- [PGP](https://rhaetor.github.io/rh-camel/components/4.8.x//dataformats/pgp-dataformat.html)

### [2.4. Endpoint Security Copy link](#endpoint_security)

Some components in Camel offer an ability to secure their endpoints (using interceptors etc) and therefore ensure that they offer the ability to secure payloads as well as provide authentication/authorization capabilities at endpoints created using the components.

### [2.5. Configuration Security Copy link](#configuration_security)

Camel offers the [Properties](https://rhaetor.github.io/rh-camel/components/4.8.x//properties-component.html) component to externalize configuration values to properties files. Those values could contain sensitive information such as usernames and passwords.

Those values can be encrypted and automatic decrypted by Camel using:

- [Jasypt](https://rhaetor.github.io/rh-camel/components/4.8.x/others/jasypt.html)

Camel also support accessing the secured configuration from an external vault systems.

#### [2.5.1. Configuration Security using Vaults Copy link](#configuration_security_using_vaults)

The following *Vaults* are supported by Camel:

- [AWS Secrets Manager](https://rhaetor.github.io/rh-camel/components/4.8.x//aws-secrets-manager-component.html)
- [Google Secret Manager](https://rhaetor.github.io/rh-camel/components/4.8.x//google-secret-manager-component.html)
- [Azure Key Vault](https://rhaetor.github.io/rh-camel/components/4.8.x//azure-key-vault-component.html)
- [Hashicorp Vault](https://rhaetor.github.io/rh-camel/components/4.8.x//hashicorp-vault-component.html)

##### [2.5.1.1. Using AWS Vault Copy link](#using_aws_vault)

To use AWS Secrets Manager you need to provide *accessKey* , *secretKey* and the *region* . This can be done using environmental variables before starting the application:

```
export $CAMEL_VAULT_AWS_ACCESS_KEY = accessKey export $CAMEL_VAULT_AWS_SECRET_KEY = secretKey export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.accessKey = accessKey
camel.vault.aws.secretKey = secretKey
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

If you want instead to use the [AWS default credentials provider](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html) , you'll need to provide the following env variables:

```
export $CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER = true export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.defaultCredentialsProvider = true
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

It is also possible to specify a particular profile name for accessing AWS Secrets Manager

```
export $CAMEL_VAULT_AWS_USE_PROFILE_CREDENTIALS_PROVIDER = true export $CAMEL_VAULT_AWS_PROFILE_NAME = test-account export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.aws.profileCredentialsProvider = true
camel.vault.aws.profileName = test-account
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way by using `aws:` as prefix in the `{{ }}` syntax:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{aws:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where `route` will be the name of the secret stored in the AWS Secrets Manager Service.

You could specify a default value in case the secret is not present on AWS Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{aws:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also, you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{
  "username": "admin",
  "password": "password123",
  "engine": "postgres",
  "host": "127.0.0.1",
  "port": "3128",
  "dbname": "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{aws:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on AWS Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{aws:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

Note

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

The only requirement is adding `camel-aws-secrets-manager` JAR to your Camel application.

##### [2.5.1.2. Using Google Secret Manager GCP Vault Copy link](#using_google_secret_manager_gcp_vault)

To use GCP Secret Manager you need to provide *serviceAccountKey* file and GCP *projectId* . This can be done using environmental variables before starting the application:

```
export $CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY = file:////path/to/service.accountkey export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.gcp.serviceAccountKey = accessKey
camel.vault.gcp.projectId = secretKey
```

Copy to Clipboard

Toggle word wrap

If you want instead to use the [GCP default client instance](https://cloud.google.com/docs/authentication/production) , you'll need to provide the following env variables:

```
export $CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = true export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.gcp.useDefaultInstance = true
camel.vault.aws.projectId = region
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way by using `gcp:` as prefix in the `{{ }}` syntax:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{gcp:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where `route` will be the name of the secret stored in the GCP Secret Manager Service.

You could specify a default value in case the secret is not present on GCP Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{gcp:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also, you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{
  "username": "admin",
  "password": "password123",
  "engine": "postgres",
  "host": "127.0.0.1",
  "port": "3128",
  "dbname": "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{gcp:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on GCP Secret Manager:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{gcp:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

Note

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

There are only two requirements: - Adding `camel-google-secret-manager` JAR to your Camel application. - Give the service account used permissions to do operation at secret management level (for example accessing the secret payload, or being admin of secret manager service)

##### [2.5.1.3. Using Azure Key Vault Copy link](#using_azure_key_vault)

To use this function you'll need to provide credentials to Azure Key Vault Service as environment variables:

```
export $CAMEL_VAULT_AZURE_TENANT_ID = tenantId export $CAMEL_VAULT_AZURE_CLIENT_ID = clientId export $CAMEL_VAULT_AZURE_CLIENT_SECRET = clientSecret export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.azure.tenantId = accessKey
camel.vault.azure.clientId = clientId
camel.vault.azure.clientSecret = clientSecret
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

Or you can enable the usage of Azure Identity in the following way:

```
export $CAMEL_VAULT_AZURE_IDENTITY_ENABLED = true export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also enable the usage of Azure Identity in the `application.properties` file such as:

```
camel.vault.azure.azureIdentityEnabled = true
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

At this point you'll be able to reference a property in the following way:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{azure:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where route will be the name of the secret stored in the Azure Key Vault Service.

You could specify a default value in case the secret is not present on Azure Key Vault Service:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{azure:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist, the property will fallback to "default" as value.

Also you are able to get particular field of the secret, if you have for example a secret named database of this form:

```
{ "username" : "admin" , "password" : "password123" , "engine" : "postgres" , "host" : "127.0.0.1" , "port" : "3128" , "dbname" : "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{azure:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on Azure Key Vault:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{azure:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case if the secret doesn't exist or the secret exists, but the username field is not part of the secret, the property will fallback to "admin" as value.

For the moment we are not considering the rotation function, if any will be applied, but it is in the work to be done.

The only requirement is adding the camel-azure-key-vault jar to your Camel application.

##### [2.5.1.4. Using Hashicorp Vault Copy link](#using_hashicorp_vault)

To use this function, you'll need to provide credentials for Hashicorp vault as environment variables:

```
export $CAMEL_VAULT_HASHICORP_TOKEN = token export $CAMEL_VAULT_HASHICORP_HOST = host export $CAMEL_VAULT_HASHICORP_PORT = port export $CAMEL_VAULT_HASHICORP_SCHEME = http/https
```

Copy to Clipboard

Toggle word wrap

You can also configure the credentials in the `application.properties` file such as:

```
camel.vault.hashicorp.token = token
camel.vault.hashicorp.host = host
camel.vault.hashicorp.port = port
camel.vault.hashicorp.scheme = scheme
```

Copy to Clipboard

Toggle word wrap

At this point, you'll be able to reference a property in the following way:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Where route will be the name of the secret stored in the Hashicorp Vault instance, in the 'secret' engine.

You could specify a default value in case the secret is not present on Hashicorp Vault instance:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route:default}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case, if the secret doesn't exist in the 'secret' engine, the property will fall back to "default" as value.

Also, you are able to get a particular field of the secret, if you have, for example, a secret named database of this form:

```
{ "username" : "admin" , "password" : "password123" , "engine" : "postgres" , "host" : "127.0.0.1" , "port" : "3128" , "dbname" : "db"
}
```

Copy to Clipboard

Toggle word wrap

You're able to do get single secret value in your route, in the 'secret' engine, like for example:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

Or re-use the property as part of an endpoint.

You could specify a default value in case the particular field of secret is not present on Hashicorp Vault instance, in the 'secret' engine:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username:admin}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

In this case, if the secret doesn't exist or the secret exists (in the 'secret' engine) but the username field is not part of the secret, the property will fall back to "admin" as value.

There is also the syntax to get a particular version of the secret for both the approach, with field/default value specified or only with secret:

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:secret:route@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the RAW route secret with version '2', in the 'secret' engine.

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <to uri="{{hashicorp:route:default@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the route secret value with version '2' or default value in case the secret doesn't exist or the version doesn't exist (in the 'secret' engine).

```
<camelContext>
    <route>
        <from uri="direct:start"/>
        <log message="Username is {{hashicorp:secret:database/username:admin@2}}"/>
    </route>
</camelContext>
```

Copy to Clipboard

Toggle word wrap

This approach will return the username field of the database secret with version '2' or admin in case the secret doesn't exist or the version doesn't exist (in the 'secret' engine).

##### [2.5.1.5. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for AWS Secret Manager Property Function).

With Environment variables:

```
export $CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER = accessKey export $CAMEL_VAULT_AWS_REGION = region
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.aws.useDefaultCredentialProvider = true
camel.vault.aws.region = region
```

Copy to Clipboard

Toggle word wrap

Or by specifying accessKey/SecretKey and region, instead of using the default credentials provider chain.

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.aws.refreshEnabled=true
camel.vault.aws.refreshPeriod=60000
camel.vault.aws.secrets=Secret
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.aws.refreshEnabled` will enable the automatic context reload, `camel.vault.aws.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.aws.secrets` is a regex representing the secrets we want to track for updates.

Note that `camel.vault.aws.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `aws:` prefix.

The only requirement is adding the camel-aws-secrets-manager jar to your Camel application.

##### [2.5.1.6. Automatic Camel context reloading on Secret Refresh while using AWS Secrets Manager with Eventbridge and AWS SQS Services Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_aws_secrets_manager_with_eventbridge_and_aws_sqs_services)

Another option is to use AWS EventBridge in conjunction with the AWS SQS service.

On the AWS side, the following resources need to be created:

- an AWS Couldtrail trail
- an AWS SQS Queue
- an Eventbridge rule of the following kind

```
{
  "source": ["aws.secretsmanager"],
  "detail-type": ["AWS API Call via CloudTrail"],
  "detail": {
    "eventSource": ["secretsmanager.amazonaws.com"]
  }
}
```

Copy to Clipboard

Toggle word wrap

This rule will make the event related to AWS Secrets Manager filtered

- You need to set the a Rule target to the AWS SQS Queue for Eventbridge rule
- You need to give permission to the Eventbrige rule, to write on the above SQS Queue. For doing this you'll need to define a json file like this:

```
{
    "Policy": "{\"Version\":\"2012-10-17\",\"Id\":\"<queue_arn>/SQSDefaultPolicy\",\"Statement\":[{\"Sid\": \"EventsToMyQueue\", \"Effect\": \"Allow\", \"Principal\": {\"Service\": \"events.amazonaws.com\"}, \"Action\": \"sqs:SendMessage\", \"Resource\": \"<queue_arn>\", \"Condition\": {\"ArnEquals\": {\"aws:SourceArn\": \"<eventbridge_rule_arn>\"}}}]}"
}
```

Copy to Clipboard

Toggle word wrap

Change the values for queue\_arn and eventbridge\_rule\_arn, save the file with policy.json name and run the following command with AWS CLI

```
aws sqs set-queue-attributes --queue-url < queue_url > --attributes file://policy.json
```

Copy to Clipboard

Toggle word wrap

where queue\_url is the AWS SQS Queue URL of the just created Queue.

Now you should be able to set up the configuration on the Camel side. To enable the SQS notification add the following properties:

```
camel.vault.aws.refreshEnabled=true
camel.vault.aws.refreshPeriod=60000
camel.vault.aws.secrets=Secret
camel.main.context-reload-enabled = true
camel.vault.aws.useSqsNotification=true
camel.vault.aws.sqsQueueUrl=<queue_url>
```

Copy to Clipboard

Toggle word wrap

where queue\_url is the AWS SQS Queue URL of the just created Queue.

Whenever an event of PutSecretValue for the Secret named 'Secret' will happen, a message will be enqueued in the AWS SQS Queue and consumed on the Camel side and a context reload will be triggered.

##### [2.5.1.7. Automatic Camel context reloading on Secret Refresh while using Google Secret Manager Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_google_secret_manager)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for Google Secret Manager Property Function).

With Environment variables:

```
export $CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = true export $CAMEL_VAULT_GCP_PROJECT_ID = projectId
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.gcp.useDefaultInstance = true
camel.vault.aws.projectId = projectId
```

Copy to Clipboard

Toggle word wrap

Or by specifying a path to a service account key file, instead of using the default instance.

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.gcp.projectId= projectId
camel.vault.gcp.refreshEnabled=true
camel.vault.gcp.refreshPeriod=60000
camel.vault.gcp.secrets=hello*
camel.vault.gcp.subscriptionName=subscriptionName
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.gcp.refreshEnabled` will enable the automatic context reload, `camel.vault.gcp.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.gcp.secrets` is a regex representing the secrets we want to track for updates.

Note that `camel.vault.gcp.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `gcp:` prefix.

The `camel.vault.gcp.subscriptionName` is the subscription name created in relation to the Google PubSub topic associated with the tracked secrets.

This mechanism while make use of the notification system related to Google Secret Manager: through this feature, every secret could be associated to one up to ten Google Pubsub Topics. These topics will receive events related to life cycle of the secret.

There are only two requirements: - Adding `camel-google-secret-manager` JAR to your Camel application. - Give the service account used permissions to do operation at secret management level (for example accessing the secret payload, or being admin of secret manager service and also have permission over the Pubsub service)

##### [2.5.1.8. Automatic Camel context reloading on Secret Refresh while using Azure Key Vault Copy link](#automatic_camel_context_reloading_on_secret_refresh_while_using_azure_key_vault)

Being able to reload Camel context on a Secret Refresh, could be done by specifying the usual credentials (the same used for Azure Key Vault Property Function).

With Environment variables:

```
export $CAMEL_VAULT_AZURE_TENANT_ID = tenantId export $CAMEL_VAULT_AZURE_CLIENT_ID = clientId export $CAMEL_VAULT_AZURE_CLIENT_SECRET = clientSecret export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

or as plain Camel main properties:

```
camel.vault.azure.tenantId = accessKey
camel.vault.azure.clientId = clientId
camel.vault.azure.clientSecret = clientSecret
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

If you want to use Azure Identity with environment variables, you can do in the following way:

```
export $CAMEL_VAULT_AZURE_IDENTITY_ENABLED = true export $CAMEL_VAULT_AZURE_VAULT_NAME = vaultName
```

Copy to Clipboard

Toggle word wrap

You can also enable the usage of Azure Identity in the `application.properties` file such as:

```
camel.vault.azure.azureIdentityEnabled = true
camel.vault.azure.vaultName = vaultName
```

Copy to Clipboard

Toggle word wrap

To enable the automatic refresh you'll need additional properties to set:

```
camel.vault.azure.refreshEnabled=true
camel.vault.azure.refreshPeriod=60000
camel.vault.azure.secrets=Secret
camel.vault.azure.eventhubConnectionString=eventhub_conn_string
camel.vault.azure.blobAccountName=blob_account_name
camel.vault.azure.blobContainerName=blob_container_name
camel.vault.azure.blobAccessKey=blob_access_key
camel.main.context-reload-enabled = true
```

Copy to Clipboard

Toggle word wrap

where `camel.vault.azure.refreshEnabled` will enable the automatic context reload, `camel.vault.azure.refreshPeriod` is the interval of time between two different checks for update events and `camel.vault.azure.secrets` is a regex representing the secrets we want to track for updates.

where `camel.vault.azure.eventhubConnectionString` is the eventhub connection string to get notification from, `camel.vault.azure.blobAccountName` , `camel.vault.azure.blobContainerName` and `camel.vault.azure.blobAccessKey` are the Azure Storage Blob parameters for the checkpoint store needed by Azure Eventhub.

Note that `camel.vault.azure.secrets` is not mandatory: if not specified the task responsible for checking updates events will take into accounts or the properties with an `azure:` prefix.

The only requirement is adding the camel-azure-key-vault jar to your Camel application.

## [Legal Notice Copy link](#idm140236916039760)

Copyright © 2025 Red Hat, Inc. The text of and illustrations in this document are licensed by Red Hat under a Creative Commons Attribution-Share Alike 3.0 Unported license ("CC-BY-SA"). An explanation of CC-BY-SA is available at [http://creativecommons.org/licenses/by-sa/3.0/](http://creativecommons.org/licenses/by-sa/3.0/) . In accordance with CC-BY-SA, if you distribute this document or an adaptation of it, you must provide the URL for the original version. Red Hat, as the licensor of this document, waives the right to enforce, and agrees not to assert, Section 4d of CC-BY-SA to the fullest extent permitted by applicable law. Red Hat, Red Hat Enterprise Linux, the Shadowman logo, the Red Hat logo, JBoss, OpenShift, Fedora, the Infinity logo, and RHCE are trademarks of Red Hat, Inc., registered in the United States and other countries.

Linux ® is the registered trademark of Linus Torvalds in the United States and other countries.

Java ® is a registered trademark of Oracle and/or its affiliates.

XFS ® is a trademark of Silicon Graphics International Corp. or its subsidiaries in the United States and/or other countries.

MySQL ® is a registered trademark of MySQL AB in the United States, the European Union and other countries.

Node.js ® is an official trademark of Joyent. Red Hat is not formally related to or endorsed by the official Joyent Node.js open source or commercial project. The OpenStack ® Word Mark and OpenStack logo are either registered trademarks/service marks or trademarks/service marks of the OpenStack Foundation, in the United States and other countries and are used with the OpenStack Foundation's permission. We are not affiliated with, endorsed or sponsored by the OpenStack Foundation, or the OpenStack community. All other trademarks are the property of their respective owners.

Format Multi-page Single-page View full doc as PDF

Red Hat logo

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Github](https://github.com/redhat-documentation)

reddit

<!-- 🖼️❌ Image not available. Please use `PdfPipelineOptions(generate_picture_images=True)` -->

[Youtube](https://www.youtube.com/@redhat) [Twitter](https://twitter.com/RedHat)

### Learn

- [Developer resources](https://developers.redhat.com/learn)
- [Cloud learning hub](/learn/learning-paths)
- [Interactive labs](https://www.redhat.com/en/interactive-labs)
- [Training and certification](https://www.redhat.com/services/training-and-certification)
- [Customer support](https://access.redhat.com/support)
- [See all documentation](/en/products)

### Try, buy, &amp; sell

- [Product trial center](https://redhat.com/en/products/trials)
- [Red Hat Ecosystem Catalog](https://catalog.redhat.com/)
- [Red Hat Store](https://www.redhat.com/en/store)
- [Buy online (Japan)](https://www.redhat.com/about/japan-buy)

### Communities

- [Customer Portal Community](https://access.redhat.com/community)
- [Events](https://www.redhat.com/events)
- [How we contribute](https://www.redhat.com/about/our-community-contributions)

### About Red Hat Documentation

We help Red Hat users innovate and achieve their goals with our products and services with content they can trust. [Explore our recent updates](https://www.redhat.com/en/blog/whats-new-docsredhatcom) .

### Making open source more inclusive

Red Hat is committed to replacing problematic language in our code, documentation, and web properties. For more details, see the [Red Hat Blog](https://www.redhat.com/en/blog/making-open-source-more-inclusive-eradicating-problematic-language) .

### About Red Hat

We deliver hardened solutions that make it easier for enterprises to work across platforms and environments, from the core datacenter to the network edge.

### Theme

- [About Red Hat](https://redhat.com/en/about/company)
- [Jobs](https://redhat.com/en/jobs)
- [Events](https://redhat.com/en/events)
- [Locations](https://redhat.com/en/about/office-locations)
- [Contact Red Hat](https://redhat.com/en/contact)
- [Red Hat Blog](https://redhat.com/en/blog)
- [Inclusion at Red Hat](https://redhat.com/en/about/our-culture/diversity-equity-inclusion)
- [Cool Stuff Store](https://coolstuff.redhat.com/)
- [Red Hat Summit](https://www.redhat.com/en/summit)

© 2026 Red Hat

- [Privacy statement](https://redhat.com/en/about/privacy-policy)
- [Terms of use](https://redhat.com/en/about/terms-use)
- [All policies and guidelines](https://redhat.com/en/about/all-policies-guidelines)
- [Digital accessibility](https://redhat.com/en/about/digital-accessibility)

Back to top