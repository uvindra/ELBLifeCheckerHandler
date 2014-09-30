/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.elb.life.checker.handler;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.util.Utils;


import javax.xml.namespace.QName;
import java.util.Map;


public class ELBLifeCheckerHandler extends AbstractHandler {
    private static final String lifeCheckerURL = "/services/ELB_LIFE_CHECKER_$$_SERVICE/pingService";
    private static final String transportURLKey = "TransportInURL";


    @Override
    public InvocationResponse invoke(MessageContext messageContext) throws AxisFault {

        String serviceURL = (String) messageContext.getProperty(transportURLKey);

        if (null != serviceURL && serviceURL.equals(lifeCheckerURL)) {
            AxisService service = messageContext.getAxisService();

            messageContext.setTo(new EndpointReference("http://localhost:8280/services/ELB_LIFE_CHECKER_$$_SERVICE/pingService"));

            if (null == service) {
                service = findService(messageContext);

                messageContext.setAxisService(service);
            }

            if (null != service) {
                setOperation(messageContext, service);
            }


        }


        return InvocationResponse.CONTINUE;
    }


    private void setOperation(MessageContext messageContext, AxisService service) throws AxisFault {
        AxisOperation operation = messageContext.getAxisOperation();

        if (null == operation) {
            operation = findOperation(service, messageContext);

            if (null != operation) {
                messageContext.setAxisOperation(operation);
            }
        }
    }


    private AxisService findService(MessageContext messageContext) throws AxisFault {
        EndpointReference toEPR = messageContext.getTo();
        if (toEPR != null) {
            String filePart = toEPR.getAddress();
            ConfigurationContext configurationContext = messageContext.getConfigurationContext();

            //Get the service/operation part from the request URL
            String serviceOpPart = Utils.getServiceAndOperationPart(filePart,
                    messageContext.getConfigurationContext().getServiceContextPath());

            if (serviceOpPart != null) {

                AxisConfiguration registry =
                        configurationContext.getAxisConfiguration();

                /**
                 * Split the serviceOpPart from '/' and add part by part and check whether we have
                 * a service. This is because we are supporting hierarchical services. We can't
                 * decide the service name just by looking at the request URL.
                 */
                AxisService axisService = null;
                String[] parts = serviceOpPart.split("/");
                String serviceName = "";
                int count = 0;

                /**
                 * To avoid performance issues if an incorrect URL comes in with a long service name
                 * including lots of '/' separated strings, we limit the hierarchical depth to 10
                 */
                while (axisService == null && count < parts.length &&
                        count < Constants.MAX_HIERARCHICAL_DEPTH) {
                    serviceName = count == 0 ? serviceName + parts[count] :
                            serviceName + "/" + parts[count];
                    axisService = registry.getService(serviceName);
                    count++;
                }

                // If the axisService is not null we get the binding that the request came to add
                // add it as a property to the messageContext
                if (axisService != null) {
                    Map endpoints = axisService.getEndpoints();
                    if (endpoints != null) {
                        if (endpoints.size() == 1) {
                            messageContext.setProperty(WSDL2Constants.ENDPOINT_LOCAL_NAME,
                                    endpoints.get(
                                            axisService.getEndpointName()));
                        } else {
                            String[] temp = serviceName.split("/");
                            int periodIndex = temp[temp.length - 1].lastIndexOf('.');
                            if (periodIndex != -1) {
                                String endpointName
                                        = temp[temp.length - 1].substring(periodIndex + 1);
                                messageContext.setProperty(WSDL2Constants.ENDPOINT_LOCAL_NAME,
                                        endpoints.get(endpointName));
                            }
                        }
                    }
                }

                return axisService;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private AxisOperation findOperation(AxisService service, MessageContext messageContext)
            throws AxisFault {

        EndpointReference toEPR = messageContext.getTo();
        if (toEPR != null) {
            String filePart = toEPR.getAddress();
            String operation  = getOperationName(filePart, service.getName());

            if (operation != null) {
                QName operationName = new QName(operation);
                return service.getOperation(operationName);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


    private String getOperationName(String path, String serviceName) {
        if (path == null || serviceName == null) {
            return null;
        }

        String[] temp = path.split("/");

        String operationName = null;
        if (temp.length > 1) {
            operationName = temp[temp.length - 1];
        } else {
            //this scenario occurs if the endpoint name is there in the URL after service name
            temp = path.split(serviceName + ".");
            if (temp.length > 1) {
                operationName = temp[temp.length - 1];
                operationName = operationName.substring(operationName.indexOf('/') + 1);
            }
        }

        if (operationName != null) {
            //remove everyting after '?'
            int queryIndex = operationName.indexOf('?');
            if (queryIndex > 0) {
                operationName = operationName.substring(0, queryIndex);
            }
            //take the part upto / as the operation name
            if (operationName.indexOf("/") != -1) {
                operationName = operationName.substring(0, operationName.indexOf("/"));
            }
        }
        return operationName;
    }
}
