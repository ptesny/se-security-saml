/* Copyright 2009 Vladimir Schäfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.saml.processor;

import org.opensaml.Configuration;
import org.opensaml.common.SAMLException;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.common.binding.security.SAMLProtocolMessageXMLSignatureSecurityPolicyRule;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.metadata.Endpoint;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.security.MetadataCredentialResolver;
import org.opensaml.ws.message.decoder.MessageDecoder;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.message.encoder.MessageEncoder;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.ws.security.SecurityPolicy;
import org.opensaml.ws.security.provider.BasicSecurityPolicy;
import org.opensaml.ws.security.provider.StaticSecurityPolicyResolver;
import org.opensaml.ws.transport.InTransport;
import org.opensaml.xml.security.credential.ChainingCredentialResolver;
import org.opensaml.xml.security.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xml.signature.impl.ExplicitKeySignatureTrustEngine;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.metadata.MetadataManager;

import java.util.Collection;

/**
 * Processor is capable of parsing SAML message from HttpServletRequest and populate the BasicSAMLMessageContext
 * for further validations.
 *
 * @author Vladimir Schäfer
 */
public class SAMLProcessorImpl implements SAMLProcessor {

    private MetadataManager metadata;
    private KeyManager keyManager;
    private Collection<SAMLBinding> bindings;

    public SAMLProcessorImpl(MetadataManager metadata, KeyManager keyManager, Collection<SAMLBinding> bindings) {
        this.metadata = metadata;
        this.bindings = bindings;
        this.keyManager = keyManager;
    }

    /**
     * Loads incoming SAML message using one of the configured bindings and populates the SAMLMessageContext object with it.
     *
     * @param samlContext context
     * @param binding     to use for message extraction
     * @return SAML message context with filled information about the message
     * @throws org.opensaml.common.SAMLException
     *          error retrieving the message from the request
     * @throws org.opensaml.saml2.metadata.provider.MetadataProviderException
     *          error retrieving metadat
     * @throws org.opensaml.ws.message.decoder.MessageDecodingException
     *          error decoding the message
     * @throws org.opensaml.xml.security.SecurityException
     *          error verifying message
     */
    public BasicSAMLMessageContext retrieveMessage(BasicSAMLMessageContext samlContext, SAMLBinding binding) throws SAMLException, MetadataProviderException, MessageDecodingException, org.opensaml.xml.security.SecurityException {

        samlContext.setLocalEntityRole(SPSSODescriptor.DEFAULT_ELEMENT_NAME);
        samlContext.setMetadataProvider(metadata);
        samlContext.setLocalEntityId(metadata.getHostedSPName());
        samlContext.setLocalEntityRoleMetadata(metadata.getRole(metadata.getHostedSPName(), SPSSODescriptor.DEFAULT_ELEMENT_NAME, SAMLConstants.SAML20P_NS));
        samlContext.setLocalEntityMetadata(metadata.getEntityDescriptor(metadata.getHostedSPName()));
        samlContext.setPeerEntityRole(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);

        ChainingCredentialResolver chainedResolver = new ChainingCredentialResolver();
        chainedResolver.getResolverChain().add(new MetadataCredentialResolver(metadata));
        chainedResolver.getResolverChain().add(keyManager);

        KeyInfoCredentialResolver keyInfoCredResolver = Configuration.getGlobalSecurityConfiguration().getDefaultKeyInfoCredentialResolver();
        ExplicitKeySignatureTrustEngine trustEngine = new ExplicitKeySignatureTrustEngine(chainedResolver, keyInfoCredResolver);
        SAMLProtocolMessageXMLSignatureSecurityPolicyRule signatureRule = new SAMLProtocolMessageXMLSignatureSecurityPolicyRule(trustEngine);

        SecurityPolicy policy = new BasicSecurityPolicy();
        policy.getPolicyRules().add(signatureRule);
        StaticSecurityPolicyResolver resolver = new StaticSecurityPolicyResolver(policy);
        samlContext.setSecurityPolicyResolver(resolver);
        samlContext.setInboundSAMLProtocol(SAMLConstants.SAML20P_NS);

        MessageDecoder decoder = binding.getMessageDecoder();
        samlContext.setCommunicationProfileId(binding.getCommunicationProfileId());
        decoder.decode(samlContext);
        samlContext.setPeerEntityId(samlContext.getPeerEntityMetadata().getEntityID());

        return samlContext;

    }

    /**
     * Loads incoming SAML message using one of the configured bindings and populates the SAMLMessageContext object with it.
     *
     * @param samlContext saml context
     * @param binding     to use for message extraction
     * @return SAML message context with filled information about the message
     * @throws org.opensaml.common.SAMLException
     *          error retrieving the message from the request
     * @throws org.opensaml.saml2.metadata.provider.MetadataProviderException
     *          error retrieving metadat
     * @throws org.opensaml.ws.message.decoder.MessageDecodingException
     *          error decoding the message
     * @throws org.opensaml.xml.security.SecurityException
     *          error verifying message
     */
    public BasicSAMLMessageContext retrieveMessage(BasicSAMLMessageContext samlContext, String binding) throws SAMLException, MetadataProviderException, MessageDecodingException, org.opensaml.xml.security.SecurityException {

        return retrieveMessage(samlContext, getBinding(binding));

    }

    /**
     * Loads incoming SAML message using one of the configured bindings and populates the SAMLMessageContext object with it.
     *
     * @param samlContext saml context
     * @return SAML message context with filled information about the message
     * @throws org.opensaml.common.SAMLException
     *          error retrieving the message from the request
     * @throws org.opensaml.saml2.metadata.provider.MetadataProviderException
     *          error retrieving metadat
     * @throws org.opensaml.ws.message.decoder.MessageDecodingException
     *          error decoding the message
     * @throws org.opensaml.xml.security.SecurityException
     *          error verifying message
     */
    public BasicSAMLMessageContext retrieveMessage(BasicSAMLMessageContext samlContext) throws SAMLException, MetadataProviderException, MessageDecodingException, org.opensaml.xml.security.SecurityException {

        return retrieveMessage(samlContext, getBinding(samlContext.getInboundMessageTransport()));

    }

    public BasicSAMLMessageContext sendMessage(BasicSAMLMessageContext samlContext, boolean sign)
            throws SAMLException, MessageEncodingException {
        Endpoint endpoint = samlContext.getPeerEntityEndpoint();
        if (endpoint == null) {
            throw new SAMLException("Could not get peer entity endpoint");
        }

        return sendMessage(samlContext, sign, getBinding(endpoint.getBinding()));
    }

    public BasicSAMLMessageContext sendMessage(BasicSAMLMessageContext samlContext, boolean sign, String bindingName) throws SAMLException, MessageEncodingException {
        return sendMessage(samlContext, sign, getBinding(bindingName));
    }

    protected BasicSAMLMessageContext sendMessage(BasicSAMLMessageContext samlContext, boolean sign, SAMLBinding binding) throws SAMLException, MessageEncodingException {
        samlContext.setLocalEntityId(metadata.getHostedSPName());
        samlContext.setMetadataProvider(metadata);

        try {
            samlContext.setLocalEntityMetadata(metadata.getEntityDescriptor(metadata.getHostedSPName()));
            samlContext.setLocalEntityRoleMetadata(metadata.getRole(metadata.getHostedSPName(), SPSSODescriptor.DEFAULT_ELEMENT_NAME, SAMLConstants.SAML20P_NS));
        } catch (MetadataProviderException e) {
            throw new SAMLException("Could not set local entity metadata.", e);
        }

        if (sign) {
            samlContext.setOutboundSAMLMessageSigningCredential(keyManager.getSPSigningCredential());
        }

        MessageEncoder encoder = binding.getMessageEncoder();
        encoder.encode(samlContext);

        return samlContext;
    }

    /**
     * Analyzes the transport object and returns the first binding capable of sending/extracing a SAML message from to/from it.
     * In case no binding is found SAMLException is thrown.
     *
     * @param transport transport type to get binding for
     * @return decoder
     * @throws SAMLException in case no suitable decoder is found for given request
     */
    protected SAMLBinding getBinding(InTransport transport) throws SAMLException {

        for (SAMLBinding binding : bindings) {
            if (binding.supports(transport)) {
                return binding;
            }
        }

        throw new SAMLException("Unsupported request");

    }

    /**
     * Finds binding with the given name.
     *
     * @param bindingName name
     * @return binding
     * @throws SAMLException in case binding can't be found
     */
    protected SAMLBinding getBinding(String bindingName) throws SAMLException {
        for (SAMLBinding binding : bindings) {
            if (binding.getCommunicationProfileId().equals(bindingName)) {
                return binding;
            }
        }
        throw new SAMLException("Binding " + bindingName + " is not available, please check your configuration");
    }
}
