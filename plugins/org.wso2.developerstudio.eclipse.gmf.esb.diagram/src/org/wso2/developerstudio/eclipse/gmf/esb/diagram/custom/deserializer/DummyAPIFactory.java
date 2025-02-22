/*
 * Copyright WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.developerstudio.eclipse.gmf.esb.diagram.custom.deserializer;

import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.aspects.AspectConfiguration;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.config.xml.rest.APIFactory;
import org.apache.synapse.config.xml.rest.ResourceFactory;
import org.apache.synapse.config.xml.rest.VersionStrategyFactory;
import org.apache.synapse.rest.API;
import org.apache.synapse.rest.version.VersionStrategy;
import org.apache.synapse.util.CommentListUtil;
import org.wso2.developerstudio.eclipse.gmf.esb.internal.persistence.custom.DummyHandler;

/**
 * Create an instance of the given Dummy API, and sets properties on it.
 * 
 */
public class DummyAPIFactory {
    private static final Log log = LogFactory.getLog(APIFactory.class);

    static final QName PROP_Q = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "property");

    static final QName ATT_NAME = new QName("name");

    static final QName ATT_VALUE = new QName("value");

    public static API createAPI(OMElement apiElt, boolean withSynapse) {
        OMAttribute nameAtt = apiElt.getAttribute(new QName("name"));
        OMAttribute contextAtt = apiElt.getAttribute(new QName("context"));
        API api;

        if (withSynapse) {
            if (nameAtt == null || "".equals(nameAtt.getAttributeValue())) {
                handleException("Attribute \"name\" is not defined for the API definition. \n"
                        + "Key value should be equal to API artifact .xml file name.");
            }

            if (contextAtt == null || "".equals(contextAtt.getAttributeValue())) {
                handleException("Attribute \"context\" is required for the API definition");
            }
            api = new API(nameAtt.getAttributeValue(), contextAtt.getAttributeValue());
            api.configure(new AspectConfiguration(nameAtt.getAttributeValue()));

        } else {
            String nameValue = "";
            String contextValue = "/";
            if (nameAtt != null) {
                nameValue = nameAtt.getAttributeValue();
            }
            if (contextAtt != null) {
                contextValue = contextAtt.getAttributeValue();
            }
            api = new API(nameValue, contextValue);
            api.configure(new AspectConfiguration(nameValue));
        }

        OMAttribute publishSwagger = apiElt.getAttribute(new QName("publishSwagger"));
        if (publishSwagger != null) {
            api.setSwaggerResourcePath(publishSwagger.getAttributeValue());
        }
        
        OMAttribute traceAtt = apiElt.getAttribute(new QName("trace"));
        if (traceAtt != null && "enable".equals(traceAtt.getAttributeValue())) {
            api.getAspectConfiguration().setTracingEnabled(true);
        }

        OMAttribute statisticsAtt = apiElt.getAttribute(new QName("statistics"));
        if (statisticsAtt != null && "enable".equals(statisticsAtt.getAttributeValue())) {
            api.getAspectConfiguration().setStatisticsEnable(true);
        }

        OMAttribute hostAtt = apiElt.getAttribute(new QName("hostname"));
        if (hostAtt != null && !"".equals(hostAtt.getAttributeValue())) {
            api.setHost(hostAtt.getAttributeValue());
        }

        VersionStrategy vStrategy = VersionStrategyFactory.createVersioningStrategy(api, apiElt);

        api.setVersionStrategy(vStrategy);

        OMAttribute portAtt = apiElt.getAttribute(new QName("port"));
        if (portAtt != null && !"".equals(portAtt.getAttributeValue())) {
            api.setPort(Integer.parseInt(portAtt.getAttributeValue()));
        }

        Iterator resources = apiElt.getChildrenWithName(new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "resource"));
        boolean noResources = true;
        while (resources.hasNext()) {
            OMElement resourceElt = (OMElement) resources.next();
            if (withSynapse) {
                api.addResource(ResourceFactory.createResource(resourceElt));
            } else {
                api.addResource(DummyResourceFactory.createResource(resourceElt));
            }
            noResources = false;
        }

        if (noResources) {
            handleException("An API must contain at least one resource definition");
        }

        OMElement handlersElt = apiElt
                .getFirstChildWithName(new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "handlers"));
        if (handlersElt != null) {
            Iterator handlers = handlersElt
                    .getChildrenWithName(new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "handler"));
            while (handlers.hasNext()) {
                OMElement handlerElt = (OMElement) handlers.next();
                defineHandler(api, handlerElt, withSynapse);
            }
        }
        
        CommentListUtil.populateComments(apiElt, api.getCommentsList());

        return api;
    }

    private static void defineHandler(API api, OMElement handlerElt, boolean withSynapse) {
        String handlerClass = handlerElt.getAttributeValue(new QName("class"));
        if (handlerClass == null || "".equals(handlerClass)) {
            handleException("A handler element must have a class attribute");
        }

        try {
            DummyHandler dummyHandler = new DummyHandler();
            dummyHandler.setClassName(handlerClass);
            api.addHandler(dummyHandler);

            for (Iterator it = handlerElt.getChildrenWithName(PROP_Q); it.hasNext();) {
                OMElement child = (OMElement) it.next();

                String propName = child.getAttribute(ATT_NAME).getAttributeValue();
                if (propName == null) {
                    handleException("A Class mediator property must specify the name attribute");
                } else {
                    if (child.getAttribute(ATT_VALUE) != null) {
                        String value = child.getAttribute(ATT_VALUE).getAttributeValue();
                        dummyHandler.addProperty(propName, value);
                    } else {
                        OMNode omElt = child.getFirstElement();
                        if (omElt != null) {
                            dummyHandler.addProperty(propName, omElt);
                        } else {
                        	if (withSynapse) {
                        		handleException("A Class mediator property must specify "
                        				+ "name and value attributes, or a name and a child XML fragment");
                        	}
                        }
                    }
                }
            }

        } catch (Exception e) {
        	if (withSynapse) {
        		handleException("Error initializing API handler: " + handlerClass, e);
        	}
        }
    }

    private static void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

    private static void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg, e);
    }

}
